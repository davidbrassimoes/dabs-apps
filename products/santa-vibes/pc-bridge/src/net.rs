//! Where to send: TVs found by their `PNMB` beacon (port 47801) plus addresses given on
//! the command line. Beacon-found TVs are dropped after `BEACON_TTL` without a beacon.

use signals_core::packet::{decode_beacon, BEACON_PORT};
use std::net::{SocketAddr, UdpSocket};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

const BEACON_TTL: Duration = Duration::from_secs(7);

#[derive(Clone, Debug)]
pub struct Target {
    pub addr: SocketAddr,
    /// None = given by hand, never expires.
    pub seen: Option<Instant>,
    pub device_id: u32,
}

#[derive(Default)]
pub struct Targets {
    list: Mutex<Vec<Target>>,
}

impl Targets {
    pub fn add_manual(&self, addr: SocketAddr) {
        self.list.lock().unwrap().push(Target { addr, seen: None, device_id: 0 });
    }

    /// Record a beacon; returns true when this TV is new.
    pub fn saw(&self, addr: SocketAddr, device_id: u32) -> bool {
        let mut l = self.list.lock().unwrap();
        if let Some(t) = l.iter_mut().find(|t| t.addr == addr || (t.seen.is_some() && t.device_id == device_id)) {
            if t.seen.is_some() {
                t.seen = Some(Instant::now());
                t.addr = addr;
            }
            return false;
        }
        l.push(Target { addr, seen: Some(Instant::now()), device_id });
        true
    }

    /// Drop expired TVs and copy the live addresses into `out` (reused, no allocation
    /// once it has grown). Returns the expired ones' addresses count.
    pub fn snapshot(&self, out: &mut Vec<SocketAddr>) -> usize {
        let mut l = self.list.lock().unwrap();
        let before = l.len();
        l.retain(|t| t.seen.is_none_or(|s| s.elapsed() < BEACON_TTL));
        out.clear();
        out.extend(l.iter().map(|t| t.addr));
        before - l.len()
    }
}

/// Listen for beacons on a background thread. Returns an error when the port is taken
/// (another bridge running); manual targets still work then.
pub fn listen_beacons(targets: Arc<Targets>) -> std::io::Result<()> {
    let sock = UdpSocket::bind(("0.0.0.0", BEACON_PORT))?;
    sock.set_read_timeout(Some(Duration::from_secs(1)))?;
    std::thread::Builder::new().name("beacon".into()).spawn(move || {
        let mut buf = [0u8; 64];
        loop {
            if let Ok((n, from)) = sock.recv_from(&mut buf) {
                if let Some(b) = decode_beacon(&buf[..n]) {
                    let addr = SocketAddr::new(from.ip(), b.signal_port);
                    if targets.saw(addr, b.device_id) {
                        crate::say!("TV found: {addr} (id {:08x}, control port {})", b.device_id, b.control_port);
                    }
                }
            }
        }
    })?;
    Ok(())
}
