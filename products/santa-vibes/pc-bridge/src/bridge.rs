//! The bridge loop: capture, analyse, send `SIGN` packets ~60 Hz (10 Hz in silence) to
//! every TV found by its beacon or given with --tv.

use crate::{capture, net, say};
use signals_core::packet::{encode_sign, flags_of, SourceKind, BEACON_PORT, SIGNAL_PORT};
use signals_core::Config;
use std::net::{SocketAddr, ToSocketAddrs, UdpSocket};
use std::sync::atomic::Ordering;
use std::sync::Arc;
use std::time::{Duration, Instant};

const ACTIVE_HZ: u32 = 60;
const SILENT_HZ: u32 = 10;
/// No callback for this long counts as "nothing playing": the sender feeds silence.
const STALE_MS: u64 = 150;
const DEVICE_POLL: Duration = Duration::from_secs(2);

pub struct Args {
    pub tvs: Vec<SocketAddr>,
    pub device: Option<String>,
    pub cfg: Config,
    pub quiet: bool,
    pub list: bool,
    /// `--autostart on|off` (console executable only).
    pub autostart: Option<bool>,
    pub stats_every: Duration,
}

pub fn parse_args(mut it: impl Iterator<Item = String>) -> Result<Args, String> {
    let mut a = Args {
        tvs: Vec::new(),
        device: None,
        cfg: Config::default(),
        quiet: false,
        list: false,
        autostart: None,
        stats_every: Duration::from_secs(5),
    };
    while let Some(k) = it.next() {
        let mut val = || it.next().ok_or(format!("{k} needs a value"));
        match k.as_str() {
            "--tv" => {
                let v = val()?;
                let with_port = if v.contains(':') { v.clone() } else { format!("{v}:{SIGNAL_PORT}") };
                let addr = with_port.to_socket_addrs().map_err(|e| format!("--tv {v}: {e}"))?.next();
                a.tvs.push(addr.ok_or(format!("--tv {v}: no address"))?);
            }
            "--device" => a.device = Some(val()?),
            "--lookahead" => a.cfg.lookahead_ms = val()?.parse().map_err(|_| "--lookahead takes ms")?,
            "--quiet" => a.quiet = true,
            "--list" => a.list = true,
            "--autostart" => {
                a.autostart = Some(match val()?.as_str() {
                    "on" => true,
                    "off" => false,
                    other => return Err(format!("--autostart takes on|off (got {other})")),
                })
            }
            "-h" | "--help" => {
                println!(
                    "pc-bridge [--tv <ip>[:port]]... [--device <name part>] [--lookahead <ms>] [--quiet]\npc-bridge --list\npc-bridge --autostart on|off"
                );
                std::process::exit(0);
            }
            _ => return Err(format!("unknown argument {k} (see --help)")),
        }
    }
    Ok(a)
}

pub fn run(args: Args) -> ! {
    let epoch = Instant::now();
    let targets = Arc::new(net::Targets::default());
    for t in &args.tvs {
        targets.add_manual(*t);
    }
    if let Err(e) = net::listen_beacons(targets.clone()) {
        say!("beacon port {BEACON_PORT} unavailable ({e}); only --tv targets will get packets");
    }
    let sock = UdpSocket::bind(("0.0.0.0", 0)).expect("UDP socket");

    let mut cap: Option<capture::Capture> = None;
    let mut next_try = Instant::now();
    let mut next_poll = Instant::now() + DEVICE_POLL;
    let mut next_stats = Instant::now() + args.stats_every;
    let mut addrs: Vec<SocketAddr> = Vec::new();
    let zeros = vec![0.0f32; 4096];
    let (mut seq, mut sent, mut beats) = (0u32, 0u32, 0u32);
    let mut next_send = Instant::now();
    let mut last_fill = Instant::now();

    say!("pc-bridge: lookahead {} ms; waiting for TV beacons on UDP {BEACON_PORT}", args.cfg.lookahead_ms);
    loop {
        let now = Instant::now();

        // (Re)open the capture: at start, after a stream error, or when the default device changes.
        if cap.as_ref().is_some_and(|c| c.shared.broken.load(Ordering::Relaxed)) {
            say!("capture lost; reopening");
            cap = None;
        }
        if now >= next_poll {
            next_poll = now + DEVICE_POLL;
            if args.device.is_none() {
                if let (Some(c), Some(def)) = (&cap, capture::default_output_id()) {
                    if c.device_id != def {
                        say!("default output changed; switching");
                        cap = None;
                        next_try = now;
                    }
                }
            }
        }
        if cap.is_none() && now >= next_try {
            next_try = now + Duration::from_secs(2);
            match capture::pick_output(args.device.as_deref()) {
                None => say!(
                    "no output device{}",
                    args.device.as_deref().map(|d| format!(" matching \"{d}\"")).unwrap_or_default()
                ),
                Some(d) => match capture::start(d, args.cfg, epoch) {
                    Ok(c) => {
                        say!("capturing \"{}\" ({} Hz, {} ch)", c.device_name, c.shared.sample_rate, c.channels);
                        cap = Some(c);
                        last_fill = now;
                    }
                    Err(e) => say!("capture: {e}"),
                },
            }
        }

        if let Some(c) = &cap {
            let sh = &c.shared;
            // Nothing playing: WASAPI sends no buffers, so advance the analyzer with silence.
            let since_audio =
                (epoch.elapsed().as_millis() as u64).saturating_sub(sh.last_audio_ms.load(Ordering::Relaxed));
            let mut a = sh.analyzer.lock().unwrap();
            if since_audio > STALE_MS {
                let n = ((now - last_fill).as_secs_f32() * sh.sample_rate as f32) as usize;
                let mut left = n.min(sh.sample_rate as usize / 2);
                while left > 0 {
                    let k = left.min(zeros.len());
                    a.push_interleaved(&zeros[..k], 1);
                    left -= k;
                }
            }
            last_fill = now;
            if now >= next_send {
                let s = a.take();
                drop(a);
                let hz = if s.silent { SILENT_HZ } else { ACTIVE_HZ };
                next_send += Duration::from_secs(1) / hz;
                if next_send < now {
                    next_send = now;
                }
                let pkt =
                    encode_sign(&s, SourceKind::PcLoopback, flags_of(&s), seq, epoch.elapsed().as_millis() as u32);
                seq = seq.wrapping_add(1);
                beats += s.onset as u32;
                targets.snapshot(&mut addrs);
                for to in &addrs {
                    if sock.send_to(&pkt, to).is_ok() {
                        sent += 1;
                    }
                }
                if !args.quiet && now >= next_stats {
                    next_stats = now + args.stats_every;
                    let secs = args.stats_every.as_secs_f32();
                    say!(
                        "{} · bpm {:5.1}{} · energy {:.2} · low {:.2} mid {:.2} high {:.2} · {:.1} beats/s · {:.0} pkt/s to {} TV(s)",
                        if s.silent { "silence" } else { "audio  " },
                        s.bpm,
                        if s.tempo_valid { "" } else { "?" },
                        s.energy,
                        s.low,
                        s.mid,
                        s.high,
                        beats as f32 / secs,
                        sent as f32 / secs,
                        addrs.len()
                    );
                    sent = 0;
                    beats = 0;
                }
            }
        }

        let wait = next_send.saturating_duration_since(Instant::now()).min(Duration::from_millis(50));
        std::thread::sleep(wait.max(Duration::from_millis(1)));
    }
}
