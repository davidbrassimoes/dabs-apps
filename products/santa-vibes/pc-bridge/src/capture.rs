//! WASAPI loopback capture of an output device, feeding the shared analyzer.
//!
//! The cpal callback pushes straight into `signals_core::Analyzer` under a mutex: the
//! analysis is ~20 µs per 10 ms of audio and allocation-free, and the sender only holds
//! the lock to copy one `Signals`. WASAPI loopback delivers nothing while no app plays
//! audio, so the sender feeds silence itself when the callback goes quiet (see main.rs).

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{Device, ErrorKind, SampleFormat, Stream};
use signals_core::{Analyzer, Config};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Instant;

pub struct Shared {
    pub analyzer: Mutex<Analyzer>,
    pub sample_rate: u32,
    /// Milliseconds since `epoch` of the last callback with audio.
    pub last_audio_ms: AtomicU64,
    /// Set by the error callback: the stream needs rebuilding.
    pub broken: AtomicBool,
    pub epoch: Instant,
}

pub struct Capture {
    pub shared: Arc<Shared>,
    pub device_name: String,
    pub device_id: String,
    pub channels: u16,
    _stream: Stream,
}

pub fn device_name(d: &Device) -> String {
    d.description().map(|x| x.name().to_string()).unwrap_or_else(|_| "?".into())
}

pub fn device_id(d: &Device) -> String {
    d.id().map(|x| x.to_string()).unwrap_or_default()
}

/// Output devices: (name, is_default).
pub fn list_outputs() -> Vec<(String, bool)> {
    let host = cpal::default_host();
    let def = host.default_output_device().map(|d| device_id(&d));
    host.output_devices()
        .map(|it| it.map(|d| (device_name(&d), Some(device_id(&d)) == def)).collect())
        .unwrap_or_default()
}

/// The output device to capture: the first whose name contains `wanted`
/// (case-insensitive), or the system default.
pub fn pick_output(wanted: Option<&str>) -> Option<Device> {
    let host = cpal::default_host();
    match wanted {
        None => host.default_output_device(),
        Some(w) => {
            let w = w.to_lowercase();
            host.output_devices().ok()?.find(|d| device_name(d).to_lowercase().contains(&w))
        }
    }
}

pub fn default_output_id() -> Option<String> {
    cpal::default_host().default_output_device().map(|d| device_id(&d))
}

pub fn start(device: Device, cfg: Config, epoch: Instant) -> Result<Capture, String> {
    let supported = device.default_output_config().map_err(|e| format!("output config: {e}"))?;
    let format = supported.sample_format();
    let config = supported.config();
    let channels = config.channels;
    let shared = Arc::new(Shared {
        analyzer: Mutex::new(Analyzer::new(config.sample_rate, cfg)),
        sample_rate: config.sample_rate,
        last_audio_ms: AtomicU64::new(0),
        broken: AtomicBool::new(false),
        epoch,
    });
    let err_shared = shared.clone();
    // WASAPI loopback reports a discontinuity (Xrun) whenever playback starts or stops, and
    // DeviceChanged when it reroutes by itself: neither needs a rebuild.
    let on_error = move |e: cpal::Error| match e.kind() {
        ErrorKind::Xrun | ErrorKind::DeviceChanged | ErrorKind::RealtimeDenied => {}
        _ => {
            crate::say!("capture: {e}");
            err_shared.broken.store(true, Ordering::Relaxed);
        }
    };
    let ch = channels as usize;
    let s = shared.clone();
    let stream = match format {
        SampleFormat::F32 => {
            device.build_input_stream(config, move |data: &[f32], _: &_| feed(&s, data, ch), on_error, None)
        }
        SampleFormat::I16 => {
            // Rare for WASAPI shared mode (the mix format is float); converted through a
            // buffer that grows once to the largest callback and is then reused.
            let mut buf: Vec<f32> = Vec::new();
            device.build_input_stream(
                config,
                move |data: &[i16], _: &_| {
                    buf.clear();
                    buf.extend(data.iter().map(|&v| v as f32 / 32768.0));
                    feed(&s, &buf, ch)
                },
                on_error,
                None,
            )
        }
        other => return Err(format!("unsupported sample format {other:?}")),
    }
    .map_err(|e| format!("loopback stream: {e}"))?;
    stream.play().map_err(|e| format!("start: {e}"))?;
    Ok(Capture { shared, device_name: device_name(&device), device_id: device_id(&device), channels, _stream: stream })
}

fn feed(s: &Shared, data: &[f32], channels: usize) {
    if data.is_empty() {
        return;
    }
    if let Ok(mut a) = s.analyzer.lock() {
        a.push_interleaved(data, channels);
    }
    s.last_audio_ms.store(s.epoch.elapsed().as_millis() as u64, Ordering::Relaxed);
}
