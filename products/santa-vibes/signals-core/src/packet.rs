//! `SIGN` and `PNMB` packets, docs/PROTOCOLO-SIGNALS.md v1. Little-endian, fixed size,
//! encoded into caller-owned arrays (no allocation).

use crate::Signals;

pub const VERSION: u8 = 1;
pub const SIGN_LEN: usize = 56;
pub const BEACON_LEN: usize = 16;
pub const SIGNAL_PORT: u16 = 47800;
pub const BEACON_PORT: u16 = 47801;

/// `sourceKind` values.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum SourceKind {
    Unknown = 0,
    PcLoopback = 1,
    PhoneMic = 2,
    PhonePlayback = 3,
    MidiClock = 4,
}

pub const FLAG_ONSET: u16 = 1 << 0;
pub const FLAG_TEMPO_VALID: u16 = 1 << 1;
pub const FLAG_MIDI_LOCKED: u16 = 1 << 2;
pub const FLAG_SILENCE: u16 = 1 << 3;

/// The flags implied by `s` (MIDI lock is the sender's business).
pub fn flags_of(s: &Signals) -> u16 {
    let mut f = 0;
    if s.onset {
        f |= FLAG_ONSET;
    }
    if s.tempo_valid {
        f |= FLAG_TEMPO_VALID;
    }
    if s.silent {
        f |= FLAG_SILENCE;
    }
    f
}

pub fn encode_sign(s: &Signals, kind: SourceKind, flags: u16, sequence: u32, sender_time_ms: u32) -> [u8; SIGN_LEN] {
    let mut b = [0u8; SIGN_LEN];
    b[0..4].copy_from_slice(b"SIGN");
    b[4] = VERSION;
    b[5] = kind as u8;
    b[6..8].copy_from_slice(&flags.to_le_bytes());
    b[8..12].copy_from_slice(&sequence.to_le_bytes());
    b[12..16].copy_from_slice(&sender_time_ms.to_le_bytes());
    let fields =
        [s.energy, s.low, s.mid, s.high, s.centroid, s.beat, s.beat_phase, s.bpm, s.mood_energy, s.mood_brightness];
    for (i, v) in fields.iter().enumerate() {
        b[16 + i * 4..20 + i * 4].copy_from_slice(&v.to_le_bytes());
    }
    b
}

/// A decoded `SIGN` packet.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Sign {
    pub kind: u8,
    pub flags: u16,
    pub sequence: u32,
    pub sender_time_ms: u32,
    pub signals: Signals,
}

/// `None` for a wrong size, magic or version (the receiver ignores those).
pub fn decode_sign(b: &[u8]) -> Option<Sign> {
    if b.len() != SIGN_LEN || &b[0..4] != b"SIGN" || b[4] != VERSION {
        return None;
    }
    let f = |i: usize| f32::from_le_bytes(b[16 + i * 4..20 + i * 4].try_into().unwrap());
    let flags = u16::from_le_bytes([b[6], b[7]]);
    Some(Sign {
        kind: b[5],
        flags,
        sequence: u32::from_le_bytes(b[8..12].try_into().unwrap()),
        sender_time_ms: u32::from_le_bytes(b[12..16].try_into().unwrap()),
        signals: Signals {
            energy: f(0),
            low: f(1),
            mid: f(2),
            high: f(3),
            centroid: f(4),
            beat: f(5),
            beat_phase: f(6),
            bpm: f(7),
            mood_energy: f(8),
            mood_brightness: f(9),
            onset: flags & FLAG_ONSET != 0,
            tempo_valid: flags & FLAG_TEMPO_VALID != 0,
            silent: flags & FLAG_SILENCE != 0,
        },
    })
}

/// A decoded `PNMB` beacon.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Beacon {
    pub signal_port: u16,
    pub control_port: u16,
    pub device_id: u32,
}

pub fn encode_beacon(b: &Beacon) -> [u8; BEACON_LEN] {
    let mut o = [0u8; BEACON_LEN];
    o[0..4].copy_from_slice(b"PNMB");
    o[4] = VERSION;
    o[6..8].copy_from_slice(&b.signal_port.to_le_bytes());
    o[8..10].copy_from_slice(&b.control_port.to_le_bytes());
    o[12..16].copy_from_slice(&b.device_id.to_le_bytes());
    o
}

pub fn decode_beacon(b: &[u8]) -> Option<Beacon> {
    if b.len() != BEACON_LEN || &b[0..4] != b"PNMB" || b[4] != VERSION {
        return None;
    }
    Some(Beacon {
        signal_port: u16::from_le_bytes([b[6], b[7]]),
        control_port: u16::from_le_bytes([b[8], b[9]]),
        device_id: u32::from_le_bytes(b[12..16].try_into().unwrap()),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sign_layout_matches_the_protocol_table() {
        let s = Signals {
            energy: 0.5,
            bpm: 120.0,
            mood_brightness: 0.25,
            onset: true,
            tempo_valid: true,
            ..Default::default()
        };
        let b = encode_sign(&s, SourceKind::PcLoopback, flags_of(&s), 0x01020304, 0xAABBCCDD);
        assert_eq!(&b[0..4], b"SIGN");
        assert_eq!(b[4], 1);
        assert_eq!(b[5], 1);
        assert_eq!(&b[6..8], &[0b11, 0]);
        assert_eq!(&b[8..12], &[4, 3, 2, 1]);
        assert_eq!(&b[12..16], &[0xDD, 0xCC, 0xBB, 0xAA]);
        assert_eq!(&b[16..20], &0.5f32.to_le_bytes());
        assert_eq!(&b[44..48], &120.0f32.to_le_bytes());
        assert_eq!(&b[52..56], &0.25f32.to_le_bytes());
        let d = decode_sign(&b).unwrap();
        assert_eq!(d.signals, s);
        assert_eq!((d.sequence, d.sender_time_ms), (0x01020304, 0xAABBCCDD));
    }

    #[test]
    fn unknown_magic_version_or_size_is_ignored() {
        let mut b = encode_sign(&Signals::default(), SourceKind::Unknown, 0, 0, 0);
        assert!(decode_sign(&b[..55]).is_none());
        b[4] = 2;
        assert!(decode_sign(&b).is_none());
        b[4] = 1;
        b[0] = b'X';
        assert!(decode_sign(&b).is_none());
    }

    #[test]
    fn beacon_round_trips() {
        let bc = Beacon { signal_port: SIGNAL_PORT, control_port: 8080, device_id: 0xDEADBEEF };
        let b = encode_beacon(&bc);
        assert_eq!(&b[0..5], b"PNMB\x01");
        assert_eq!(&b[6..8], &47800u16.to_le_bytes());
        assert_eq!(decode_beacon(&b), Some(bc));
    }
}
