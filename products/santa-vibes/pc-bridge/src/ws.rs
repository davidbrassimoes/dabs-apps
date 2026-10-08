//! Local WebSocket feed for the authoring tool (ROADMAP 1.6): every `SIGN` packet the TVs get
//! is also sent, as is, as one binary frame to each connected browser tab. Only on 127.0.0.1
//! and only for pages served from this PC (Origin check), so nothing leaves the machine.
//!
//! Hand-written on `std::net` (RFC 6455 handshake: SHA-1 + base64), no dependency: the server
//! only ever sends small unmasked binary frames and never reads after the handshake.

use std::io::{BufRead, BufReader, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::{Arc, Mutex};
use std::time::Duration;

pub const DEFAULT_PORT: u16 = 47810;
const GUID: &str = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

#[derive(Default)]
pub struct Clients {
    list: Mutex<Vec<TcpStream>>,
}

impl Clients {
    /// Send one binary frame to every client; drop the ones that fail (tab closed).
    pub fn broadcast(&self, payload: &[u8]) {
        let mut l = self.list.lock().unwrap();
        if l.is_empty() {
            return;
        }
        let mut frame = [0u8; 2 + 125];
        let n = payload.len().min(125);
        frame[0] = 0x82; // FIN + binary
        frame[1] = n as u8;
        frame[2..2 + n].copy_from_slice(&payload[..n]);
        l.retain_mut(|s| s.write_all(&frame[..2 + n]).is_ok());
    }

    pub fn count(&self) -> usize {
        self.list.lock().unwrap().len()
    }
}

/// Accept connections on a background thread. Errors when the port is taken.
pub fn serve(port: u16, clients: Arc<Clients>) -> std::io::Result<()> {
    let listener = TcpListener::bind(("127.0.0.1", port))?;
    std::thread::Builder::new().name("ws".into()).spawn(move || {
        for stream in listener.incoming().flatten() {
            match handshake(&stream) {
                Ok(()) => {
                    let _ = stream.set_nodelay(true);
                    // A stalled tab must not block the sender loop for long.
                    let _ = stream.set_write_timeout(Some(Duration::from_millis(20)));
                    clients.list.lock().unwrap().push(stream);
                    crate::say!("authoring connected ({} open)", clients.count());
                }
                Err(e) => crate::say!("ws: refused a connection ({e})"),
            }
        }
    })?;
    Ok(())
}

fn handshake(stream: &TcpStream) -> Result<(), String> {
    stream.set_read_timeout(Some(Duration::from_secs(2))).map_err(|e| e.to_string())?;
    let mut reader = BufReader::new(stream);
    let (mut key, mut origin, mut upgrade) = (None, None, false);
    let mut line = String::new();
    loop {
        line.clear();
        if reader.read_line(&mut line).map_err(|e| e.to_string())? == 0 {
            return Err("closed during handshake".into());
        }
        let l = line.trim_end();
        if l.is_empty() {
            break;
        }
        if let Some((k, v)) = l.split_once(':') {
            let v = v.trim();
            match k.trim().to_ascii_lowercase().as_str() {
                "sec-websocket-key" => key = Some(v.to_string()),
                "origin" => origin = Some(v.to_string()),
                "upgrade" => upgrade = v.eq_ignore_ascii_case("websocket"),
                _ => {}
            }
        }
    }
    let mut out = stream;
    if !upgrade || key.is_none() || !origin.as_deref().is_none_or(local_origin) {
        let _ = out.write_all(b"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n");
        return Err(format!("not a local WebSocket request (origin {origin:?})"));
    }
    let resp = format!(
        "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: {}\r\n\r\n",
        accept_key(&key.unwrap())
    );
    out.write_all(resp.as_bytes()).map_err(|e| e.to_string())
}

/// Pages served from this PC: the authoring dev server (any port) or a file.
pub fn local_origin(o: &str) -> bool {
    let host = o.strip_prefix("http://").unwrap_or("");
    let host = host.split(':').next().unwrap_or("");
    host == "localhost" || host == "127.0.0.1" || o == "null"
}

pub fn accept_key(key: &str) -> String {
    base64(&sha1(format!("{key}{GUID}").as_bytes()))
}

pub fn sha1(data: &[u8]) -> [u8; 20] {
    let mut h: [u32; 5] = [0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0];
    let mut msg = data.to_vec();
    let bits = (data.len() as u64) * 8;
    msg.push(0x80);
    while msg.len() % 64 != 56 {
        msg.push(0);
    }
    msg.extend_from_slice(&bits.to_be_bytes());
    for block in msg.chunks(64) {
        let mut w = [0u32; 80];
        for (i, c) in block.chunks(4).enumerate() {
            w[i] = u32::from_be_bytes([c[0], c[1], c[2], c[3]]);
        }
        for i in 16..80 {
            w[i] = (w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16]).rotate_left(1);
        }
        let [mut a, mut b, mut c, mut d, mut e] = h;
        for (i, wi) in w.iter().enumerate() {
            let (f, k) = match i {
                0..=19 => ((b & c) | (!b & d), 0x5A827999),
                20..=39 => (b ^ c ^ d, 0x6ED9EBA1),
                40..=59 => ((b & c) | (b & d) | (c & d), 0x8F1BBCDC),
                _ => (b ^ c ^ d, 0xCA62C1D6),
            };
            let t = a.rotate_left(5).wrapping_add(f).wrapping_add(e).wrapping_add(k).wrapping_add(*wi);
            e = d;
            d = c;
            c = b.rotate_left(30);
            b = a;
            a = t;
        }
        for (x, y) in h.iter_mut().zip([a, b, c, d, e]) {
            *x = x.wrapping_add(y);
        }
    }
    let mut out = [0u8; 20];
    for (i, v) in h.iter().enumerate() {
        out[i * 4..i * 4 + 4].copy_from_slice(&v.to_be_bytes());
    }
    out
}

pub fn base64(data: &[u8]) -> String {
    const T: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut s = String::new();
    for c in data.chunks(3) {
        let n = (c[0] as u32) << 16 | (*c.get(1).unwrap_or(&0) as u32) << 8 | *c.get(2).unwrap_or(&0) as u32;
        for i in 0..4 {
            if i <= c.len() {
                s.push(T[(n >> (18 - 6 * i) & 63) as usize] as char);
            } else {
                s.push('=');
            }
        }
    }
    s
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rfc6455_example_key() {
        assert_eq!(accept_key("dGhlIHNhbXBsZSBub25jZQ=="), "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
    }

    #[test]
    fn sha1_and_base64_known_values() {
        assert_eq!(base64(&sha1(b"abc")), "qZk+NkcGgWq6PiVxeFDCbJzQ2J0=");
        assert_eq!(base64(b"f"), "Zg==");
        assert_eq!(base64(b"fo"), "Zm8=");
        assert_eq!(base64(b"foo"), "Zm9v");
    }

    #[test]
    fn only_local_pages() {
        assert!(local_origin("http://localhost:5180"));
        assert!(local_origin("http://127.0.0.1:5180"));
        assert!(!local_origin("https://example.com"));
        assert!(!local_origin("http://localhost.evil.com"));
    }
}
