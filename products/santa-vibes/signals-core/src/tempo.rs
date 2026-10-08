//! Onsets, tempo and beat phase from the onset detection function (ODF, spectral flux),
//! one value per analysis frame. Buffers are sized in [`Tempo::new`]; nothing allocates
//! afterwards.
//!
//! - Onsets: local maximum of the ODF above mean + 3 std of the last second, at least
//!   0.1 s apart (reported one frame late, when the peak is confirmed).
//! - Tempo: autocorrelation of the last 6 s of ODF, every 0.25 s, lags for 60..180 BPM,
//!   score = prior(bpm) * (acf[lag] + 0.5 acf[2 lag]), prior a log-Gaussian (1 octave)
//!   around 120 BPM; parabolic refinement; a maximum at either end of the range is skipped
//!   (a slope, not a tempo); a new tempo must repeat 3 times to replace the current one (4
//!   when nothing is locked yet); double or half the current tempo confirms it instead of
//!   replacing it; 3 s of silence forget it. Tuned on 8 tracks, 2026-10-08 (`NOTES.md`).
//! - Phase: comb over the last beats (weights 0.8^k) picks how many frames ago the last
//!   beat was; a PLL (gain 0.35) pulls the free-running phase towards it.

const BPM_MIN: f32 = 60.0;
const BPM_MAX: f32 = 180.0;
const HISTORY_S: f32 = 6.0;
const MIN_HISTORY_S: f32 = 3.0;
const UPDATE_S: f32 = 0.25;
const CONFIDENCE_MIN: f32 = 0.3;
/// Onset threshold: mean + ONSET_K std of the last second of ODF.
const ONSET_K: f32 = 3.0;
/// Equal estimates needed before the first tempo is declared (one every UPDATE_S).
const START_HITS: u32 = 4;
/// Equal estimates needed to replace the current tempo.
const CHANGE_HITS: u32 = 3;
/// After this much continuous silence the tempo is dropped: what comes next is probably another
/// song (a short gap between notes does not count).
const SILENCE_FORGET_S: f32 = 3.0;

pub struct Tempo {
    fps: f32,
    hist: Vec<f32>,
    lin: Vec<f32>,
    acf: Vec<f32>,
    pos: usize,
    count: usize,
    min_lag: usize,
    max_lag: usize,
    update_every: usize,
    since_update: usize,
    since_onset: usize,
    onset_window: usize,
    candidate: f32,
    candidate_hits: u32,
    silent_frames: usize,
    /// Current tempo (0 = none) and its period in frames.
    pub bpm: f32,
    period: f32,
    pub confidence: f32,
    pub valid: bool,
    /// Phase inside the beat at the newest frame: 0 on the beat, rising to 1.
    pub phase: f32,
    phase_locked: bool,
}

impl Tempo {
    pub fn new(fps: f32) -> Self {
        let h = (HISTORY_S * fps).ceil() as usize;
        let max_lag = (60.0 * fps / BPM_MIN).round() as usize;
        Self {
            fps,
            hist: vec![0.0; h],
            lin: vec![0.0; h],
            acf: vec![0.0; 2 * max_lag + 2],
            pos: 0,
            count: 0,
            min_lag: (60.0 * fps / BPM_MAX).round() as usize,
            max_lag,
            update_every: ((UPDATE_S * fps).round() as usize).max(1),
            since_update: 0,
            since_onset: usize::MAX / 2,
            onset_window: (fps.round() as usize).max(4),
            candidate: 0.0,
            candidate_hits: 0,
            silent_frames: 0,
            bpm: 0.0,
            period: 0.0,
            confidence: 0.0,
            valid: false,
            phase: 0.0,
            phase_locked: false,
        }
    }

    /// Call once per frame before [`Tempo::push`]: whether the input is silent.
    pub fn note_silence(&mut self, silent: bool) {
        if !silent {
            self.silent_frames = 0;
            return;
        }
        self.silent_frames += 1;
        if self.silent_frames == (SILENCE_FORGET_S * self.fps) as usize {
            self.count = 0; // the old song must not lock again from the history
            self.bpm = 0.0;
            self.valid = false;
            self.confidence = 0.0;
            self.phase_locked = false;
            self.candidate_hits = 0;
        }
    }

    /// ODF value `age` frames ago (0 = newest).
    fn odf(&self, age: usize) -> f32 {
        let h = self.hist.len();
        self.hist[(self.pos + h - 1 - age) % h]
    }

    /// Add one ODF value; returns true when an onset was confirmed (it happened one frame ago).
    pub fn push(&mut self, flux: f32) -> bool {
        self.hist[self.pos] = flux;
        self.pos = (self.pos + 1) % self.hist.len();
        self.count = self.count.saturating_add(1);
        self.since_onset = self.since_onset.saturating_add(1);

        if self.valid {
            self.phase = (self.phase + 1.0 / self.period).fract();
        }
        self.since_update += 1;
        if self.since_update >= self.update_every && self.count as f32 >= MIN_HISTORY_S * self.fps {
            self.since_update = 0;
            self.estimate_tempo();
            if self.valid {
                self.estimate_phase();
            }
        }
        self.pick_onset()
    }

    fn pick_onset(&mut self) -> bool {
        let w = self.onset_window.min(self.count);
        if w < 4 {
            return false;
        }
        let (o0, o1, o2) = (self.odf(0), self.odf(1), self.odf(2));
        if !(o1 > o2 && o1 >= o0) || (self.since_onset as f32) < 0.1 * self.fps {
            return false;
        }
        let mut sum = 0.0;
        let mut sq = 0.0;
        for a in 0..w {
            let v = self.odf(a);
            sum += v;
            sq += v * v;
        }
        let mean = sum / w as f32;
        let std = (sq / w as f32 - mean * mean).max(0.0).sqrt();
        if o1 > mean + ONSET_K * std && o1 > 1e-3 {
            self.since_onset = 1;
            return true;
        }
        false
    }

    fn estimate_tempo(&mut self) {
        let len = self.count.min(self.hist.len());
        let mean = (0..len).map(|a| self.odf(a)).sum::<f32>() / len as f32;
        // Centre and smooth (triangle, 5 taps): sharp ODF peaks would otherwise miss
        // integer lags when the period is fractional (150 BPM = 37.5 frames at 94 fps).
        for a in 0..len {
            let at = |d: isize| self.odf((a as isize + d).clamp(0, len as isize - 1) as usize) - mean;
            self.lin[a] = (at(-2) + 2.0 * at(-1) + 3.0 * at(0) + 2.0 * at(1) + at(2)) / 9.0;
        }
        let lin = &self.lin[..len];
        let max_acf_lag = (2 * self.max_lag + 1).min(len - 1);
        for lag in 0..self.acf.len() {
            self.acf[lag] = if lag <= max_acf_lag {
                let n = len - lag;
                lin[..n].iter().zip(&lin[lag..]).map(|(a, b)| a * b).sum::<f32>() / n as f32
            } else {
                0.0
            };
        }
        let acf0 = self.acf[0];
        if acf0 <= 1e-12 {
            self.confidence = 0.0;
            self.valid = false;
            return;
        }
        let score = |s: &Self, lag: usize| -> f32 {
            let bpm = 60.0 * s.fps / lag as f32;
            let oct = (bpm / 120.0).log2();
            let prior = (-0.5 * oct * oct).exp();
            let double = s.acf.get(2 * lag).copied().unwrap_or(0.0);
            prior * (s.acf[lag] + 0.5 * double) / acf0
        };
        let mut best = self.min_lag;
        let mut best_score = f32::MIN;
        for lag in self.min_lag..=self.max_lag {
            let sc = score(self, lag);
            if sc > best_score {
                best_score = sc;
                best = lag;
            }
        }
        // A maximum at either end of the lag range is a slope, not a tempo (the step from silence
        // into music gave 181.5 BPM, the top of the range): skip this estimate.
        if best == self.min_lag || best == self.max_lag {
            return;
        }
        // Parabolic refinement around the peak.
        let mut lag_f = best as f32;
        if best > self.min_lag && best < self.max_lag {
            let (a, b, c) = (score(self, best - 1), best_score, score(self, best + 1));
            let den = a - 2.0 * b + c;
            if den.abs() > 1e-12 {
                lag_f += (0.5 * (a - c) / den).clamp(-0.5, 0.5);
            }
        }
        self.confidence = self.acf[best] / acf0;
        let bpm = 60.0 * self.fps / lag_f;
        let close = |a: f32, b: f32| b > 0.0 && (a / b - 1.0).abs() < 0.04;
        // Double or half the current tempo confirms it at the current level: music often supports
        // both, and flipping between them makes the picture pulse twice as fast and back.
        let same_level = if close(bpm, 2.0 * self.bpm) {
            bpm / 2.0
        } else if close(2.0 * bpm, self.bpm) {
            bpm * 2.0
        } else {
            bpm
        };

        if self.bpm > 0.0 && close(same_level, self.bpm) {
            self.bpm += 0.3 * (same_level - self.bpm);
            self.candidate_hits = 0;
        } else {
            if close(bpm, self.candidate) {
                self.candidate_hits += 1;
            } else {
                self.candidate = bpm;
                self.candidate_hits = 1;
            }
            let needed = if self.bpm <= 0.0 { START_HITS } else { CHANGE_HITS };
            if self.candidate_hits >= needed {
                self.bpm = self.candidate;
                self.candidate_hits = 0;
                self.phase_locked = false;
            }
        }
        self.valid = self.bpm > 0.0 && self.confidence > CONFIDENCE_MIN;
        if self.bpm > 0.0 {
            self.period = 60.0 * self.fps / self.bpm;
        }
    }

    fn estimate_phase(&mut self) {
        let len = self.count.min(self.hist.len());
        let p = self.period;
        let mut best = 0usize;
        let mut best_score = f32::MIN;
        for phi in 0..p.ceil() as usize {
            let mut s = 0.0;
            let mut w = 1.0;
            let mut k = 0;
            loop {
                let age = (phi as f32 + k as f32 * p).round() as usize;
                if age >= len || k >= 8 {
                    break;
                }
                s += w * self.odf(age);
                w *= 0.8;
                k += 1;
            }
            if s > best_score {
                best_score = s;
                best = phi;
            }
        }
        let measured = (best as f32 / p).fract();
        if !self.phase_locked {
            self.phase = measured;
            self.phase_locked = true;
        } else {
            let mut e = measured - self.phase;
            e -= e.round(); // wrap to [-0.5, 0.5]
            self.phase = (self.phase + 0.35 * e).rem_euclid(1.0);
        }
    }

    pub fn period_s(&self) -> f32 {
        if self.bpm > 0.0 {
            60.0 / self.bpm
        } else {
            0.0
        }
    }
}
