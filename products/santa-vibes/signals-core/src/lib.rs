//! Santa Vibes signal analysis (docs/ARQUITETURA.md, "Fonte de sinal"; docs/PROTOCOLO-SIGNALS.md).
//!
//! Feed PCM blocks to [`Analyzer::push_interleaved`] from the capture callback; read the
//! latest [`Signals`] with [`Analyzer::take`] at the send rate (~60 Hz) and serialise them
//! with [`packet::encode_sign`]. After [`Analyzer::new`] nothing allocates.
//!
//! Pipeline per hop (fft size / 4 samples, ~94 Hz at 44.1/48 kHz):
//! Hann window → FFT → band levels (dB) → AGC → fast smoothing; spectral flux → onset
//! detection function → onsets, tempo (autocorrelation) and beat phase (comb + PLL).
//! Algorithms and constants are described in signals-core/README.md.

mod agc;
mod analyzer;
pub mod packet;
mod spectrum;
mod tempo;
pub mod wav;

pub use analyzer::{Analyzer, Config};

/// One analysis result, all values normalised (0..1 unless noted). Maps 1:1 onto the
/// `SIGN` packet fields.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct Signals {
    pub energy: f32,
    pub low: f32,
    pub mid: f32,
    pub high: f32,
    /// Spectral brightness, log-frequency mapped (150 Hz → 0, 6 kHz → 1).
    pub centroid: f32,
    /// Beat envelope: 1 on a beat, decays (tau 0.18 s, like the tv-app Synth).
    pub beat: f32,
    /// Predicted position inside the beat (0..1), `Config::lookahead_ms` ahead.
    pub beat_phase: f32,
    /// Estimated tempo; 0 when unknown.
    pub bpm: f32,
    pub mood_energy: f32,
    pub mood_brightness: f32,
    /// A beat fired since the last [`Analyzer::take`].
    pub onset: bool,
    /// `bpm` and `beat_phase` are trustworthy.
    pub tempo_valid: bool,
    /// Input below the silence threshold.
    pub silent: bool,
}
