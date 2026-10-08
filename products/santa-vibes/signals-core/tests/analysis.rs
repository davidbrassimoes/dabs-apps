//! End-to-end analysis on generated audio.

mod common;

use common::*;
use signals_core::{wav, Config};

fn assert_bpm(got: f32, want: f32, what: &str) {
    assert!((got / want - 1.0).abs() < 0.02, "{what}: bpm {got}, want {want}");
}

#[test]
fn tempo_locks_on_kick_tracks() {
    for bpm in [90.0, 120.0, 128.0, 150.0] {
        let (audio, _) = kicks(48000, 10.0, 0.5, |_| bpm);
        let s = last(48000, &audio);
        assert!(s.tempo_valid, "{bpm}: not valid");
        assert_bpm(s.bpm, bpm, &format!("{bpm} @ 48k"));
    }
}

#[test]
fn tempo_locks_at_every_sample_rate() {
    for sr in [16000, 44100, 48000] {
        let (audio, _) = kicks(sr, 10.0, 0.5, |_| 120.0);
        let s = last(sr, &audio);
        assert_bpm(s.bpm, 120.0, &format!("{sr} Hz"));
    }
}

#[test]
fn predicted_beats_land_on_the_real_ones() {
    // With lookahead 0, fired beats must sit on the true beats (frame resolution ~11 ms,
    // plus the analysis window): within 40 ms once locked.
    let sr = 48000;
    let (audio, beats) = kicks(sr, 16.0, 0.5, |_| 120.0);
    let cfg = Config { lookahead_ms: 0.0, ..Config::default() };
    let mut fired = Vec::new();
    run(sr, cfg, &audio, |t, a| {
        if a.take().onset && t > 8.0 {
            fired.push(t);
        }
    });
    let expected = beats.iter().filter(|&&b| b > 8.0 && b < 15.9).count();
    assert!((fired.len() as i32 - expected as i32).abs() <= 1, "fired {} of {}", fired.len(), expected);
    for t in &fired {
        let err = beats.iter().map(|b| (t - b).abs()).fold(f32::MAX, f32::min);
        assert!(err < 0.04, "beat at {t:.3}s is {:.0} ms off", err * 1000.0);
    }
}

#[test]
fn lookahead_moves_the_beats_earlier() {
    let sr = 48000;
    let (audio, beats) = kicks(sr, 16.0, 0.5, |_| 120.0);
    let mean_offset = |lookahead_ms: f32| {
        let mut offs = Vec::new();
        run(sr, Config { lookahead_ms, ..Config::default() }, &audio, |t, a| {
            if a.take().onset && t > 8.0 {
                let nearest = beats.iter().copied().min_by(|x, y| (t - x).abs().total_cmp(&(t - y).abs())).unwrap();
                offs.push(t - nearest);
            }
        });
        offs.iter().sum::<f32>() / offs.len() as f32
    };
    let shift = mean_offset(0.0) - mean_offset(100.0);
    assert!((shift - 0.1).abs() < 0.025, "shift {shift}");
    // A negative lookahead delays them instead (the room's speakers can be slower than the TV).
    let back = mean_offset(0.0) - mean_offset(-50.0);
    assert!((back + 0.05).abs() < 0.025, "back {back}");
}

#[test]
fn tempo_follows_a_change() {
    let sr = 44100;
    let (audio, _) = kicks(sr, 20.0, 0.5, |t| if t < 10.0 { 100.0 } else { 130.0 });
    let mut at = Vec::new();
    run(sr, Config::default(), &audio, |t, a| {
        if (t - 9.5).abs() < 0.005 || (t - 16.0).abs() < 0.005 {
            at.push(a.peek().bpm);
        }
    });
    assert_bpm(at[0], 100.0, "before");
    assert_bpm(at[1], 130.0, "6 s after the change");
}

#[test]
fn silence_is_flagged_and_quiet() {
    let s = last(48000, &vec![0.0; 48000 * 3]);
    assert!(s.silent && !s.onset && !s.tempo_valid);
    assert_eq!((s.energy, s.low, s.mid, s.high, s.beat), (0.0, 0.0, 0.0, 0.0, 0.0));
}

#[test]
fn steady_noise_has_no_tempo() {
    let mut beats = 0;
    let a = run(48000, Config::default(), &noise(48000, 10.0, 0.3), |_, a| beats += a.take().onset as u32);
    assert!(!a.peek().tempo_valid, "bpm {}", a.peek().bpm);
    assert!(beats < 10, "{beats} beats in 10 s of noise");
}

#[test]
fn bands_separate_low_and_high() {
    let low = last(48000, &tone(48000, 2.0, 60.0, 0.5));
    assert!(low.low > 0.8 && low.mid < 0.2 && low.high < 0.2, "{low:?}");
    let high = last(48000, &tone(48000, 2.0, 5000.0, 0.5));
    assert!(high.high > 0.8 && high.low < 0.2 && high.mid < 0.2, "{high:?}");
    assert!(low.centroid < 0.1 && high.centroid > 0.85);
}

#[test]
fn agc_makes_loudness_irrelevant() {
    let mean_energy = |gain: f32| {
        let (audio, _) = kicks(48000, 12.0, gain, |_| 120.0);
        let mut sum = 0.0;
        let mut n = 0;
        run(48000, Config::default(), &audio, |t, a| {
            if t > 8.0 {
                sum += a.peek().energy;
                n += 1;
            }
        });
        sum / n as f32
    };
    let (loud, quiet) = (mean_energy(0.5), mean_energy(0.03));
    assert!((loud - quiet).abs() < 0.15, "loud {loud} quiet {quiet}");
}

#[test]
fn mood_moves_slowly() {
    let mut audio = noise(48000, 20.0, 0.3);
    audio.extend(noise(48000, 3.0, 0.003));
    let mut before = None;
    let a = run(48000, Config::default(), &audio, |t, a| {
        if before.is_none() && t >= 20.0 {
            before = Some(*a.peek());
        }
    });
    let (b, s) = (before.unwrap(), *a.peek());
    let fast_drop = b.energy - s.energy;
    let mood_drop = b.mood_energy - s.mood_energy;
    assert!(fast_drop > 0.5, "fast {fast_drop}");
    assert!(mood_drop < 0.2 * fast_drop, "mood {mood_drop} vs fast {fast_drop}");
}

#[test]
fn a_wav_file_round_trip_gives_the_same_tempo() {
    let sr = 44100;
    let (mono, _) = kicks(sr, 10.0, 0.5, |_| 128.0);
    let stereo: Vec<f32> = mono.iter().flat_map(|&v| [v, v * 0.8]).collect();
    let path = std::env::temp_dir().join("signals-core-kicks-128.wav");
    wav::write(std::fs::File::create(&path).unwrap(), sr, 2, &stereo).unwrap();
    let w = wav::read(std::fs::File::open(&path).unwrap()).unwrap();
    let mut a = signals_core::Analyzer::new(w.sample_rate, Config::default());
    a.push_interleaved(&w.samples, w.channels as usize);
    assert_bpm(a.peek().bpm, 128.0, "from WAV");
    let _ = std::fs::remove_file(path);
}

#[test]
fn a_full_groove_locks_on_the_beat_not_the_hats() {
    for bpm in [96.0, 124.0, 140.0] {
        let s = last(44100, &groove(44100, 12.0, bpm));
        assert!(s.tempo_valid, "{bpm}: not valid");
        assert_bpm(s.bpm, bpm, &format!("groove {bpm}"));
    }
}

#[test]
fn without_a_tempo_beats_come_from_onsets() {
    // Irregular hits (no steady period): every hit should still fire a beat.
    let sr = 48000;
    let hits = [0.5, 1.3, 1.7, 2.9, 3.2, 4.6, 5.1];
    let mut audio = vec![0.0f32; sr as usize * 6];
    let mut rng = Rng::new(5);
    for h in hits {
        let s = (h * sr as f32) as usize;
        for i in 0..2400 {
            audio[s + i] += 0.6 * rng.next() * (-(i as f32) / 400.0).exp();
        }
    }
    let mut fired = Vec::new();
    run(sr, Config::default(), &audio, |t, a| {
        let s = a.take();
        if s.onset {
            assert!(!s.tempo_valid);
            fired.push(t);
        }
    });
    assert_eq!(fired.len(), hits.len(), "{fired:?}");
    for (f, h) in fired.iter().zip(hits) {
        assert!(f - h >= 0.0 && f - h < 0.06, "hit {h} fired at {f}");
    }
}

#[test]
fn music_after_silence_never_reads_the_range_edge() {
    // The step from silence into music used to give 181.5 BPM (the top of the lag range) for
    // about a second before the real tempo (seen with every source, 2026-10-07).
    let sr = 48000;
    let (k, _) = kicks(sr, 10.0, 0.5, |_| 120.0);
    let mut audio = vec![0.0; 4 * sr as usize];
    audio.extend(k);
    let mut readings = Vec::new();
    run(sr, Config::default(), &audio, |_, a| {
        let s = a.peek();
        if s.tempo_valid {
            readings.push(s.bpm);
        }
    });
    assert!(!readings.is_empty(), "never locked");
    assert!(readings.iter().all(|&b| b < 175.0), "edge tempo seen: {:?}", readings.iter().fold(0.0f32, |m, &b| m.max(b)));
    assert_bpm(*readings.last().unwrap(), 120.0, "after silence");
}

#[test]
fn a_double_time_layer_does_not_flip_the_tempo() {
    // Music often supports both levels (85 and 170 in a rock song); flipping between them makes
    // the picture pulse twice as fast and back. Once locked, double or half confirms the tempo.
    let sr = 48000;
    let (slow, _) = kicks(sr, 24.0, 0.5, |_| 90.0);
    let (fast, _) = kicks(sr, 24.0, 0.6, |_| 180.0);
    let start = 8 * sr as usize;
    let audio: Vec<f32> = slow.iter().enumerate().map(|(i, &x)| x + if i >= start { fast[i] } else { 0.0 }).collect();
    let mut late = Vec::new();
    run(sr, Config::default(), &audio, |t, a| {
        let s = a.peek();
        if s.tempo_valid && t > 6.0 {
            late.push(s.bpm);
        }
    });
    assert!(late.iter().all(|&b| (b / 90.0 - 1.0).abs() < 0.04), "flipped: {:?}", late.iter().fold(0.0f32, |m, &b| m.max(b)));
}

#[test]
fn long_silence_forgets_the_tempo() {
    let sr = 48000;
    let (mut audio, _) = kicks(sr, 8.0, 0.5, |_| 120.0);
    audio.extend(vec![0.0; 4 * sr as usize]);
    assert!(!last(sr, &audio).tempo_valid, "tempo kept through 4 s of silence");
}
