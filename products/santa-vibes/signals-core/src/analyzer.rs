//! The analysis pipeline: PCM in, [`Signals`] out. See lib.rs and the README.

use crate::agc::{Agc, Smooth};
use crate::spectrum::Spectrum;
use crate::tempo::Tempo;
use crate::Signals;

#[derive(Clone, Copy, Debug)]
pub struct Config {
    /// How far ahead `beat_phase` (and the beat it fires) is predicted, to cover analysis,
    /// network and render latency. Tuned against the real chain in step 1.5.
    pub lookahead_ms: f32,
    /// Below this overall level (dBFS) the input counts as silence.
    pub silence_db: f32,
}

impl Default for Config {
    fn default() -> Self {
        Self { lookahead_ms: 60.0, silence_db: -60.0 }
    }
}

const BEAT_DECAY_S: f32 = 0.18;
const MOOD_S: f32 = 30.0;
/// A band more than this far below the overall peak level reads 0.
const BAND_SPAN_DB: f32 = 20.0;

pub struct Analyzer {
    cfg: Config,
    ring: Vec<f32>,
    ring_pos: usize,
    hop: usize,
    since_hop: usize,
    dt: f32,
    spectrum: Spectrum,
    tempo: Tempo,
    agc: [Agc; 4],
    fast: [Smooth; 4],
    centroid: Smooth,
    mood_agc: Agc,
    mood_energy: Smooth,
    mood_brightness: Smooth,
    out_phase: f32,
    sig: Signals,
}

impl Analyzer {
    /// FFT size: the power of two closest above 40 ms of audio (2048 at 44.1/48 kHz,
    /// 1024 at 16 kHz); hop = a quarter of it.
    pub fn new(sample_rate: u32, cfg: Config) -> Self {
        let sr = sample_rate as f32;
        let n = ((sr * 0.04) as usize).next_power_of_two().max(256);
        let hop = n / 4;
        let fps = sr / hop as f32;
        // Fast bands: 36 dB window, 1 dB/s release. Mood: 0.1 dB/s, so whole sections
        // of a set (quiet intro vs. drop) still read as different.
        let band_agc = || Agc::new(36.0, 1.0, -50.0);
        Self {
            cfg,
            ring: vec![0.0; n],
            ring_pos: 0,
            hop,
            since_hop: 0,
            dt: 1.0 / fps,
            spectrum: Spectrum::new(n, sr),
            tempo: Tempo::new(fps),
            agc: [band_agc(), band_agc(), band_agc(), band_agc()],
            fast: [Smooth::new(0.015, 0.15); 4],
            centroid: Smooth::new(0.1, 0.1),
            mood_agc: Agc::new(36.0, 0.1, -50.0),
            mood_energy: Smooth::new(MOOD_S, MOOD_S),
            mood_brightness: Smooth::new(MOOD_S, MOOD_S),
            out_phase: 0.0,
            sig: Signals::default(),
        }
    }

    /// Analysis frames per second (sample rate / hop).
    pub fn frame_rate(&self) -> f32 {
        1.0 / self.dt
    }

    /// Feed interleaved PCM (`channels` ≥ 1, downmixed to mono). Runs one analysis frame
    /// per completed hop; returns how many ran. Allocation-free.
    pub fn push_interleaved(&mut self, samples: &[f32], channels: usize) -> usize {
        let ch = channels.max(1);
        let inv = 1.0 / ch as f32;
        let mut frames = 0;
        for chunk in samples.chunks_exact(ch) {
            self.ring[self.ring_pos] = chunk.iter().sum::<f32>() * inv;
            self.ring_pos = (self.ring_pos + 1) % self.ring.len();
            self.since_hop += 1;
            if self.since_hop == self.hop {
                self.since_hop = 0;
                self.frame();
                frames += 1;
            }
        }
        frames
    }

    /// The latest values; clears the onset latch (call once per packet sent).
    pub fn take(&mut self) -> Signals {
        let s = self.sig;
        self.sig.onset = false;
        s
    }

    /// The latest values without clearing the onset latch.
    pub fn peek(&self) -> &Signals {
        &self.sig
    }

    fn frame(&mut self) {
        let dt = self.dt;
        let (head, tail) = (&self.ring[self.ring_pos..], &self.ring[..self.ring_pos]);
        let f = self.spectrum.analyze(head.iter().chain(tail).copied());
        let silent = f.band_db[3] < self.cfg.silence_db;

        // Overall level first; each band's AGC floor sits BAND_SPAN_DB below its peak.
        let mut v = [0.0f32; 4];
        let e = if silent { 0.0 } else { self.agc[3].update(f.band_db[3], dt) };
        v[3] = self.fast[3].update(e, dt);
        let floor = self.agc[3].peak() - BAND_SPAN_DB;
        #[allow(clippy::needless_range_loop)] // four parallel arrays, indexed together
        for i in 0..3 {
            let x = if silent { 0.0 } else { self.agc[i].update_with_floor(f.band_db[i], dt, floor) };
            v[i] = self.fast[i].update(x, dt);
        }
        let centroid = if silent { self.centroid.value } else { self.centroid.update(f.centroid, dt) };
        let loud = if silent { 0.0 } else { self.mood_agc.update(f.band_db[3], dt) };
        self.mood_energy.update(loud, dt);
        self.mood_brightness.update(centroid, dt);

        self.tempo.note_silence(silent);
        let raw_onset = self.tempo.push(if silent { 0.0 } else { f.flux });
        let s = &mut self.sig;
        s.low = v[0];
        s.mid = v[1];
        s.high = v[2];
        s.energy = v[3];
        s.centroid = centroid;
        s.mood_energy = self.mood_energy.value;
        s.mood_brightness = self.mood_brightness.value;
        s.silent = silent;
        s.tempo_valid = self.tempo.valid;
        s.bpm = if self.tempo.valid { self.tempo.bpm } else { 0.0 };

        // With a tempo, beats come from the predicted phase (ahead by lookahead_ms);
        // without one, from the detected onsets.
        let fire = if self.tempo.valid {
            let ahead = self.cfg.lookahead_ms / 1000.0 / self.tempo.period_s();
            // rem_euclid, not fract: a negative lookahead (the picture would lead the sound) must wrap to 0..1.
            let ph = (self.tempo.phase + ahead).rem_euclid(1.0);
            let wrapped = ph < self.out_phase && self.out_phase - ph > 0.5;
            self.out_phase = ph;
            wrapped
        } else {
            self.out_phase = 0.0;
            raw_onset
        };
        s.beat_phase = self.out_phase;
        if fire && !silent {
            s.beat = 1.0;
            s.onset = true;
        } else {
            s.beat *= (-dt / BEAT_DECAY_S).exp();
        }
    }
}

impl Analyzer {
    /// Tempo confidence: normalised autocorrelation at the chosen lag (0..1). Diagnostics.
    pub fn tempo_confidence(&self) -> f32 {
        self.tempo.confidence
    }
}
