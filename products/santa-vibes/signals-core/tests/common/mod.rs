//! Deterministic test audio: kick tracks, tones and noise.
#![allow(dead_code)]

use signals_core::{Analyzer, Config, Signals};

pub struct Rng(u32);

impl Rng {
    pub fn new(seed: u32) -> Self {
        Self(seed.max(1))
    }
    /// Uniform in -1..1.
    pub fn next(&mut self) -> f32 {
        self.0 ^= self.0 << 13;
        self.0 ^= self.0 >> 17;
        self.0 ^= self.0 << 5;
        self.0 as f32 / u32::MAX as f32 * 2.0 - 1.0
    }
}

/// A kick drum (55 Hz sine with a pitch drop, 150 ms decay) plus a short noise click,
/// every beat, over a quiet noise bed. `bpm_at(t)` gives the tempo at time t.
pub fn kicks(sr: u32, seconds: f32, gain: f32, bpm_at: impl Fn(f32) -> f32) -> (Vec<f32>, Vec<f32>) {
    let n = (sr as f32 * seconds) as usize;
    let mut out = vec![0.0; n];
    let mut beats = Vec::new();
    let mut rng = Rng::new(7);
    let mut t_beat = 0.25;
    while t_beat < seconds {
        beats.push(t_beat);
        let start = (t_beat * sr as f32) as usize;
        for i in 0..(sr as f32 * 0.3) as usize {
            let k = start + i;
            if k >= n {
                break;
            }
            let t = i as f32 / sr as f32;
            let f = 55.0 + 60.0 * (-t / 0.02).exp();
            let body = (std::f32::consts::TAU * f * t).sin() * (-t / 0.15).exp();
            let click = if t < 0.005 { rng.next() * 0.5 } else { 0.0 };
            out[k] += 0.8 * body + click;
        }
        t_beat += 60.0 / bpm_at(t_beat);
    }
    for v in out.iter_mut() {
        *v = (*v + 0.02 * rng.next()) * gain;
    }
    (out, beats)
}

pub fn tone(sr: u32, seconds: f32, hz: f32, amp: f32) -> Vec<f32> {
    (0..(sr as f32 * seconds) as usize)
        .map(|i| amp * (std::f32::consts::TAU * hz * i as f32 / sr as f32).sin())
        .collect()
}

pub fn noise(sr: u32, seconds: f32, amp: f32) -> Vec<f32> {
    let mut r = Rng::new(99);
    (0..(sr as f32 * seconds) as usize).map(|_| amp * r.next()).collect()
}

/// Run mono audio through an analyzer in 10 ms blocks, calling `each` after every block
/// with the time at the end of the block.
pub fn run(sr: u32, cfg: Config, audio: &[f32], mut each: impl FnMut(f32, &mut Analyzer)) -> Analyzer {
    let mut a = Analyzer::new(sr, cfg);
    let block = (sr / 100) as usize;
    let mut done = 0;
    for chunk in audio.chunks(block) {
        a.push_interleaved(chunk, 1);
        done += chunk.len();
        each(done as f32 / sr as f32, &mut a);
    }
    a
}

pub fn last(sr: u32, audio: &[f32]) -> Signals {
    *run(sr, Config::default(), audio, |_, _| {}).peek()
}

/// A drum pattern at `bpm`: kick on every beat, snare (noise burst) on 2 and 4, closed
/// hi-hats on every eighth, and a held A2 note underneath.
pub fn groove(sr: u32, seconds: f32, bpm: f32) -> Vec<f32> {
    let (mut out, beats) = kicks(sr, seconds, 0.5, |_| bpm);
    let mut rng = Rng::new(3);
    let eighth = 30.0 / bpm;
    let n = out.len();
    let mut add = |at: f32, len_s: f32, decay_s: f32, gain: f32, rng: &mut Rng| {
        let start = (at * sr as f32) as usize;
        for i in 0..(len_s * sr as f32) as usize {
            if start + i >= n {
                break;
            }
            let t = i as f32 / sr as f32;
            out[start + i] += gain * rng.next() * (-t / decay_s).exp();
        }
    };
    for (i, &b) in beats.iter().enumerate() {
        if i % 2 == 1 {
            add(b, 0.2, 0.06, 0.35, &mut rng);
        }
        add(b, 0.05, 0.01, 0.08, &mut rng);
        add(b + eighth, 0.05, 0.01, 0.08, &mut rng);
    }
    for (i, v) in out.iter_mut().enumerate() {
        *v += 0.1 * (std::f32::consts::TAU * 110.0 * i as f32 / sr as f32).sin();
    }
    out
}
