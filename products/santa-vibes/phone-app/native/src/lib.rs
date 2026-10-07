//! signals-core for the phone app: an analyzer behind a mutex (the audio thread pushes,
//! the sender thread takes packets), exposed to Kotlin through four JNI functions on
//! `dev.santavibes.phone.SignalsNative`. Nothing allocates after `create`.

mod jni;

use signals_core::packet::{encode_sign, flags_of, SourceKind, SIGN_LEN};
use signals_core::{Analyzer, Config};
use std::sync::Mutex;

pub struct Engine {
    analyzer: Mutex<Analyzer>,
}

impl Engine {
    pub fn new(sample_rate: u32, lookahead_ms: f32) -> Self {
        let cfg = Config { lookahead_ms, ..Config::default() };
        Self { analyzer: Mutex::new(Analyzer::new(sample_rate, cfg)) }
    }

    pub fn push(&self, samples: &[f32], channels: usize) {
        if let Ok(mut a) = self.analyzer.lock() {
            a.push_interleaved(samples, channels);
        }
    }

    /// Take the latest signals and encode a `SIGN` packet into `out`; returns its flags.
    pub fn packet(&self, kind: u8, sequence: u32, time_ms: u32, out: &mut [u8]) -> u16 {
        let s = match self.analyzer.lock() {
            Ok(mut a) => a.take(),
            Err(_) => return 0,
        };
        let kind = match kind {
            2 => SourceKind::PhoneMic,
            3 => SourceKind::PhonePlayback,
            _ => SourceKind::Unknown,
        };
        let flags = flags_of(&s);
        let b = encode_sign(&s, kind, flags, sequence, time_ms);
        let n = out.len().min(SIGN_LEN);
        out[..n].copy_from_slice(&b[..n]);
        flags
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use signals_core::packet::decode_sign;

    #[test]
    fn a_tone_comes_out_as_a_phone_packet() {
        let e = Engine::new(48_000, 60.0);
        let tone: Vec<f32> = (0..48_000).map(|i| 0.5 * (i as f32 * 0.05).sin()).collect();
        for chunk in tone.chunks(960) {
            e.push(chunk, 1);
        }
        let mut out = [0u8; 56];
        let flags = e.packet(3, 7, 1234, &mut out);
        let p = decode_sign(&out).unwrap();
        assert_eq!((p.kind, p.sequence, p.sender_time_ms, p.flags), (3, 7, 1234, flags));
        assert!(p.signals.energy > 0.5 && !p.signals.silent);
    }
}
