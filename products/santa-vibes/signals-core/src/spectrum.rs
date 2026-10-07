//! Windowed FFT of the latest `n` samples: band levels, centroid and spectral flux.
//! Buffers are allocated in [`Spectrum::new`]; [`Spectrum::analyze`] allocates nothing.

use rustfft::num_complex::Complex32;
use rustfft::{Fft, FftPlanner};
use std::sync::Arc;

/// Band edges in Hz: low, mid, high. `energy` spans all three.
pub const BANDS: [(f32, f32); 3] = [(20.0, 250.0), (250.0, 2000.0), (2000.0, 12000.0)];
const FLUX_HZ: (f32, f32) = (30.0, 5000.0);
const CENTROID_LO: f32 = 150.0;
const CENTROID_HI: f32 = 6000.0;

pub struct Frame {
    /// Mean-square level per band (low, mid, high, all) in dB re full scale (sine = -3 dB).
    pub band_db: [f32; 4],
    /// Spectral centroid, log-mapped to 0..1.
    pub centroid: f32,
    /// Half-wave rectified flux of log-compressed magnitudes (onset detection function).
    pub flux: f32,
}

pub struct Spectrum {
    fft: Arc<dyn Fft<f32>>,
    window: Vec<f32>,
    buf: Vec<Complex32>,
    scratch: Vec<Complex32>,
    prev_mag: Vec<f32>,
    /// Bin ranges [start, end) per band, then the flux range.
    band_bins: [(usize, usize); 3],
    flux_bins: (usize, usize),
    bin_hz: f32,
    norm_power: f32,
    norm_mag: f32,
}

impl Spectrum {
    pub fn new(n: usize, sample_rate: f32) -> Self {
        let fft = FftPlanner::new().plan_fft_forward(n);
        let window: Vec<f32> =
            (0..n).map(|i| 0.5 - 0.5 * (std::f32::consts::TAU * i as f32 / n as f32).cos()).collect();
        let wsq: f32 = window.iter().map(|w| w * w).sum();
        let bin_hz = sample_rate / n as f32;
        let nyq = n / 2;
        let bin = |hz: f32| ((hz / bin_hz).round() as usize).clamp(1, nyq);
        let scratch = vec![Complex32::default(); fft.get_inplace_scratch_len()];
        Self {
            fft,
            window,
            buf: vec![Complex32::default(); n],
            scratch,
            prev_mag: vec![0.0; nyq + 1],
            band_bins: [
                (bin(BANDS[0].0), bin(BANDS[0].1)),
                (bin(BANDS[1].0), bin(BANDS[1].1)),
                (bin(BANDS[2].0), bin(BANDS[2].1)),
            ],
            flux_bins: (bin(FLUX_HZ.0), bin(FLUX_HZ.1)),
            bin_hz,
            // Parseval, one-sided: mean square of the windowed input = 2 Σ|X|² / (n Σw²).
            norm_power: 2.0 / (n as f32 * wsq),
            // Hann coherent gain 0.5: a full-scale sine peaks at |X| = n/4.
            norm_mag: 4.0 / n as f32,
        }
    }

    /// Analyse `samples` (exactly `n`, oldest first).
    pub fn analyze(&mut self, samples: impl Iterator<Item = f32>) -> Frame {
        for ((c, s), w) in self.buf.iter_mut().zip(samples).zip(&self.window) {
            *c = Complex32::new(s * w, 0.0);
        }
        self.fft.process_with_scratch(&mut self.buf, &mut self.scratch);

        let mut band_pow = [0.0f32; 3];
        for (b, &(lo, hi)) in self.band_bins.iter().enumerate() {
            band_pow[b] = self.buf[lo..hi].iter().map(|c| c.norm_sqr()).sum::<f32>();
        }
        let (lo, hi) = (self.band_bins[0].0, self.band_bins[2].1);
        let mut weighted = 0.0f32;
        let mut total = 0.0f32;
        for k in lo..hi {
            let p = self.buf[k].norm_sqr();
            weighted += p * k as f32;
            total += p;
        }
        let centroid_hz = if total > 0.0 { weighted / total * self.bin_hz } else { 0.0 };
        let centroid = if centroid_hz > 0.0 {
            ((centroid_hz / CENTROID_LO).log2() / (CENTROID_HI / CENTROID_LO).log2()).clamp(0.0, 1.0)
        } else {
            0.0
        };

        let mut flux = 0.0f32;
        for k in self.flux_bins.0..self.flux_bins.1 {
            let m = (1.0 + 100.0 * self.buf[k].norm() * self.norm_mag).ln();
            let d = m - self.prev_mag[k];
            if d > 0.0 {
                flux += d;
            }
            self.prev_mag[k] = m;
        }
        flux /= (self.flux_bins.1 - self.flux_bins.0) as f32;

        let db = |p: f32| 10.0 * (p * self.norm_power + 1e-12).log10();
        Frame {
            band_db: [db(band_pow[0]), db(band_pow[1]), db(band_pow[2]), db(band_pow.iter().sum())],
            centroid,
            flux,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sine(hz: f32, amp: f32, sr: f32, n: usize) -> impl Iterator<Item = f32> {
        (0..n).map(move |i| amp * (std::f32::consts::TAU * hz * i as f32 / sr).sin())
    }

    #[test]
    fn full_scale_sine_reads_minus_3_db_in_its_band() {
        let mut s = Spectrum::new(2048, 48000.0);
        let f = s.analyze(sine(1000.0, 1.0, 48000.0, 2048));
        assert!((f.band_db[1] + 3.0).abs() < 0.5, "{:?}", f.band_db);
        assert!(f.band_db[0] < -40.0 && f.band_db[2] < -40.0, "{:?}", f.band_db);
        let g = s.analyze(sine(1000.0, 0.1, 48000.0, 2048));
        assert!((g.band_db[1] + 23.0).abs() < 0.5);
    }

    #[test]
    fn centroid_follows_pitch() {
        let mut s = Spectrum::new(2048, 44100.0);
        let lo = s.analyze(sine(200.0, 0.5, 44100.0, 2048)).centroid;
        let hi = s.analyze(sine(4000.0, 0.5, 44100.0, 2048)).centroid;
        assert!(lo < 0.2 && hi > 0.8, "{lo} {hi}");
    }

    #[test]
    fn flux_is_zero_for_a_steady_tone_and_positive_on_an_attack() {
        let mut s = Spectrum::new(1024, 16000.0);
        s.analyze(sine(440.0, 0.0, 16000.0, 1024));
        assert!(s.analyze(sine(440.0, 0.5, 16000.0, 1024)).flux > 0.01);
        assert!(s.analyze(sine(440.0, 0.5, 16000.0, 1024)).flux < 1e-4);
    }
}
