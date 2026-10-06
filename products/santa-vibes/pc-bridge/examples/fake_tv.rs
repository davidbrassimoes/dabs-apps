//! A stand-in for the TV until the tv-app receives signals (step 1.3): broadcasts the
//! `PNMB` beacon every 2 s (also to 127.0.0.1, for a bridge on the same PC), listens for
//! `SIGN` packets and prints one line per second: rate, lost/out-of-order packets, gaps
//! (the tv-app treats 1.5 s without packets as "source absent") and the latest values.
//!
//!   cargo run --release --example fake_tv [-- --seconds 30]

use signals_core::packet::{decode_sign, encode_beacon, Beacon, BEACON_PORT, SIGNAL_PORT};
use std::net::UdpSocket;
use std::time::{Duration, Instant};

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let limit =
        args.iter().position(|a| a == "--seconds").and_then(|i| args.get(i + 1)).and_then(|v| v.parse::<u64>().ok());
    let rx = UdpSocket::bind(("0.0.0.0", SIGNAL_PORT)).expect("bind 47800 (is another fake_tv running?)");
    rx.set_read_timeout(Some(Duration::from_millis(50))).unwrap();
    let tx = UdpSocket::bind(("0.0.0.0", 0)).unwrap();
    tx.set_broadcast(true).unwrap();
    let beacon = encode_beacon(&Beacon { signal_port: SIGNAL_PORT, control_port: 8080, device_id: 0x5ade_f00d });

    let start = Instant::now();
    let (mut next_beacon, mut next_line) = (start, start + Duration::from_secs(1));
    let (mut n, mut lost, mut reordered, mut onsets, mut max_gap) = (0u32, 0u32, 0u32, 0u32, Duration::ZERO);
    let mut last_seq: Option<u32> = None;
    let mut last_rx: Option<Instant> = None;
    let mut last = None;
    let mut buf = [0u8; 128];
    println!("fake TV on UDP {SIGNAL_PORT}, beacon to 255.255.255.255:{BEACON_PORT} and 127.0.0.1:{BEACON_PORT}");
    loop {
        let now = Instant::now();
        if limit.is_some_and(|s| now - start >= Duration::from_secs(s)) {
            break;
        }
        if now >= next_beacon {
            next_beacon += Duration::from_secs(2);
            let _ = tx.send_to(&beacon, ("255.255.255.255", BEACON_PORT));
            let _ = tx.send_to(&beacon, ("127.0.0.1", BEACON_PORT));
        }
        if let Ok((len, _)) = rx.recv_from(&mut buf) {
            if let Some(p) = decode_sign(&buf[..len]) {
                let t = Instant::now();
                if let Some(prev) = last_rx {
                    max_gap = max_gap.max(t - prev);
                }
                last_rx = Some(t);
                match last_seq {
                    Some(s) if p.sequence <= s => reordered += 1,
                    Some(s) => lost += p.sequence - s - 1,
                    None => {}
                }
                if last_seq.is_none_or(|s| p.sequence > s) {
                    last_seq = Some(p.sequence);
                }
                n += 1;
                onsets += p.signals.onset as u32;
                last = Some(p);
            }
        }
        if now >= next_line {
            next_line += Duration::from_secs(1);
            match last {
                Some(p) => {
                    let s = p.signals;
                    let bar = "#".repeat((s.energy * 20.0) as usize);
                    println!(
                        "{:3}s {:3} pkt/s lost {lost} reord {reordered} max gap {:4} ms{} · bpm {:5.1}{} phase {:.2} beats {onsets} · E {:.2} L {:.2} M {:.2} H {:.2} C {:.2} mood {:.2}/{:.2} {bar}",
                        (now - start).as_secs(),
                        n,
                        max_gap.as_millis(),
                        if p.flags & 8 != 0 { " (silence)" } else { "" },
                        s.bpm,
                        if p.flags & 2 != 0 { "" } else { "?" },
                        s.beat_phase,
                        s.energy,
                        s.low,
                        s.mid,
                        s.high,
                        s.centroid,
                        s.mood_energy,
                        s.mood_brightness
                    );
                }
                None => println!("{:3}s no packets", (now - start).as_secs()),
            }
            n = 0;
            onsets = 0;
            max_gap = Duration::ZERO;
        }
    }
    println!("total lost {lost}, out of order {reordered}");
}
