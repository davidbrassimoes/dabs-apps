//! Run the analyzer over a WAV file, as a sender would at 60 Hz, and print one line per
//! second plus the cost per analysis frame. For tuning against real music:
//!
//!   cargo run --release --example analyze -- song.wav [--csv out.csv] [--lookahead 60]

use signals_core::{wav, Analyzer, Config};
use std::time::Instant;

fn main() -> std::io::Result<()> {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let Some(path) = args.first() else {
        eprintln!("usage: analyze <file.wav> [--csv out.csv] [--lookahead ms]");
        std::process::exit(2);
    };
    let opt = |name: &str| args.iter().position(|a| a == name).and_then(|i| args.get(i + 1)).cloned();
    let mut cfg = Config::default();
    if let Some(ms) = opt("--lookahead") {
        cfg.lookahead_ms = ms.parse().expect("--lookahead takes milliseconds");
    }
    let w = wav::read(std::fs::File::open(path)?)?;
    let ch = w.channels as usize;
    let mut a = Analyzer::new(w.sample_rate, cfg);
    let mut csv = opt("--csv").map(|p| std::fs::File::create(p).expect("csv"));
    if let Some(f) = csv.as_mut() {
        use std::io::Write;
        writeln!(f, "t,energy,low,mid,high,centroid,beat,phase,bpm,mood_energy,mood_brightness,onset,valid,silent")?;
    }
    println!(
        "{path}: {} Hz, {} ch, {:.1} s, {:.1} analysis frames/s",
        w.sample_rate,
        ch,
        w.samples.len() as f32 / ch as f32 / w.sample_rate as f32,
        a.frame_rate()
    );
    println!("   t   bpm   conf  energy  low   mid   high  centr  moodE moodB  beats/s");

    let block = (w.sample_rate as usize / 60) * ch; // one packet's worth
    let (mut t, mut beats, mut next_line, mut frames) = (0.0f32, 0, 1.0f32, 0usize);
    let mut busy = std::time::Duration::ZERO;
    for chunk in w.samples.chunks(block) {
        let start = Instant::now();
        frames += a.push_interleaved(chunk, ch);
        let s = a.take();
        busy += start.elapsed();
        t += chunk.len() as f32 / ch as f32 / w.sample_rate as f32;
        beats += s.onset as u32;
        if let Some(f) = csv.as_mut() {
            use std::io::Write;
            writeln!(
                f,
                "{t:.3},{:.3},{:.3},{:.3},{:.3},{:.3},{:.3},{:.3},{:.2},{:.3},{:.3},{},{},{}",
                s.energy,
                s.low,
                s.mid,
                s.high,
                s.centroid,
                s.beat,
                s.beat_phase,
                s.bpm,
                s.mood_energy,
                s.mood_brightness,
                s.onset as u8,
                s.tempo_valid as u8,
                s.silent as u8
            )?;
        }
        if t >= next_line {
            next_line += 1.0;
            println!(
                "{t:5.0} {:5.1} {:5.2}  {:.2}   {:.2}  {:.2}  {:.2}  {:.2}   {:.2}  {:.2}   {beats}{}",
                s.bpm,
                a.tempo_confidence(),
                s.energy,
                s.low,
                s.mid,
                s.high,
                s.centroid,
                s.mood_energy,
                s.mood_brightness,
                if s.silent { "  (silence)" } else { "" }
            );
            beats = 0;
        }
    }
    println!(
        "cost: {:.1} µs per analysis frame ({frames} frames), {:.3}% of one core in real time",
        busy.as_secs_f64() * 1e6 / frames.max(1) as f64,
        busy.as_secs_f64() / t as f64 * 100.0
    );
    Ok(())
}
