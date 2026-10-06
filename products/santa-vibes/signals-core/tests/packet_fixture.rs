//! tests/fixtures/sign-cases.json is the language-neutral contract for the packet layout:
//! the tv-app (Kotlin) decodes the same bytes and must get the same values. This test
//! checks that the encoder still produces the file; regenerate it on purpose with
//!   SV_WRITE_FIXTURES=1 cargo test --test packet_fixture

use signals_core::packet::{encode_beacon, encode_sign, flags_of, Beacon, SourceKind};
use signals_core::Signals;
use std::fmt::Write;

fn hex(b: &[u8]) -> String {
    b.iter().map(|x| format!("{x:02x}")).collect()
}

fn fixture() -> String {
    let cases: [(Signals, SourceKind, u16, u32, u32); 4] = [
        (Signals::default(), SourceKind::Unknown, 0, 0, 0),
        (
            Signals {
                energy: 0.5,
                low: 0.75,
                mid: 0.25,
                high: 0.125,
                centroid: 0.3,
                beat: 1.0,
                beat_phase: 0.02,
                bpm: 123.5,
                mood_energy: 0.4,
                mood_brightness: 0.6,
                onset: true,
                tempo_valid: true,
                silent: false,
            },
            SourceKind::PcLoopback,
            0,
            41,
            123_456,
        ),
        (Signals { silent: true, mood_energy: 0.1, ..Default::default() }, SourceKind::PhoneMic, 0, 4_000_000_000, u32::MAX),
        (Signals { bpm: 90.0, beat_phase: 0.999, tempo_valid: true, ..Default::default() }, SourceKind::MidiClock, 1 << 2, 7, 1),
    ];
    let mut out = String::from("{\n \"sign\": [\n");
    for (i, (s, kind, extra, seq, t)) in cases.iter().enumerate() {
        let flags = flags_of(s) | extra;
        let b = encode_sign(s, *kind, flags, *seq, *t);
        let v = [s.energy, s.low, s.mid, s.high, s.centroid, s.beat, s.beat_phase, s.bpm, s.mood_energy, s.mood_brightness];
        let vals: Vec<String> = v.iter().map(|x| format!("{x:?}")).collect();
        write!(
            out,
            "  {{\"hex\": \"{}\", \"kind\": {}, \"flags\": {flags}, \"sequence\": {seq}, \"senderTimeMs\": {t}, \"values\": [{}]}}{}\n",
            hex(&b),
            *kind as u8,
            vals.join(", "),
            if i + 1 < cases.len() { "," } else { "" }
        )
        .unwrap();
    }
    let bc = Beacon { signal_port: 47800, control_port: 0, device_id: 0x1234_abcd };
    write!(
        out,
        " ],\n \"beacon\": {{\"hex\": \"{}\", \"signalPort\": 47800, \"controlPort\": 0, \"deviceId\": {}}}\n}}\n",
        hex(&encode_beacon(&bc)),
        bc.device_id
    )
    .unwrap();
    out
}

#[test]
fn fixture_matches_the_encoder() {
    let path = concat!(env!("CARGO_MANIFEST_DIR"), "/tests/fixtures/sign-cases.json");
    let want = fixture();
    if std::env::var("SV_WRITE_FIXTURES").is_ok() {
        std::fs::create_dir_all(concat!(env!("CARGO_MANIFEST_DIR"), "/tests/fixtures")).unwrap();
        std::fs::write(path, &want).unwrap();
    }
    let got = std::fs::read_to_string(path).expect("fixture missing: run with SV_WRITE_FIXTURES=1");
    assert_eq!(got.replace("\r\n", "\n"), want);
}
