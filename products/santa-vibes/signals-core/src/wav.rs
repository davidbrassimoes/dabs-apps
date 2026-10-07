//! Minimal WAV (RIFF) reader and writer for tests and the `analyze` example: PCM 16/24/32
//! bit and IEEE float 32. Not used on the capture path.
#![allow(clippy::chunks_exact_to_as_chunks)] // one readable line per encoding

use std::io::{self, Read, Write};

pub struct Wav {
    pub sample_rate: u32,
    pub channels: u16,
    /// Interleaved, -1..1.
    pub samples: Vec<f32>,
}

fn bad(msg: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, msg.to_string())
}

pub fn read(mut r: impl Read) -> io::Result<Wav> {
    let mut data = Vec::new();
    r.read_to_end(&mut data)?;
    if data.len() < 12 || &data[0..4] != b"RIFF" || &data[8..12] != b"WAVE" {
        return Err(bad("not a RIFF/WAVE file"));
    }
    let (mut fmt, mut pos) = (None, 12);
    while pos + 8 <= data.len() {
        let id = &data[pos..pos + 4];
        let len = u32::from_le_bytes(data[pos + 4..pos + 8].try_into().unwrap()) as usize;
        let body = &data[pos + 8..(pos + 8 + len).min(data.len())];
        if id == b"fmt " && body.len() >= 16 {
            let tag = u16::from_le_bytes([body[0], body[1]]);
            let tag = if tag == 0xFFFE && body.len() >= 26 { u16::from_le_bytes([body[24], body[25]]) } else { tag };
            fmt = Some((
                tag,
                u16::from_le_bytes([body[2], body[3]]),
                u32::from_le_bytes(body[4..8].try_into().unwrap()),
                u16::from_le_bytes([body[14], body[15]]),
            ));
        } else if id == b"data" {
            let (tag, channels, sample_rate, bits) = fmt.ok_or_else(|| bad("data before fmt"))?;
            let samples = match (tag, bits) {
                (1, 16) => body.chunks_exact(2).map(|c| i16::from_le_bytes([c[0], c[1]]) as f32 / 32768.0).collect(),
                (1, 24) => body
                    .chunks_exact(3)
                    .map(|c| (i32::from_le_bytes([0, c[0], c[1], c[2]]) >> 8) as f32 / 8_388_608.0)
                    .collect(),
                (1, 32) => body
                    .chunks_exact(4)
                    .map(|c| i32::from_le_bytes(c.try_into().unwrap()) as f32 / 2_147_483_648.0)
                    .collect(),
                (3, 32) => body.chunks_exact(4).map(|c| f32::from_le_bytes(c.try_into().unwrap())).collect(),
                _ => return Err(bad("unsupported WAV encoding (PCM 16/24/32 or float 32)")),
            };
            return Ok(Wav { sample_rate, channels, samples });
        }
        pos += 8 + len + (len & 1);
    }
    Err(bad("no data chunk"))
}

/// Write 16-bit PCM.
pub fn write(mut w: impl Write, sample_rate: u32, channels: u16, samples: &[f32]) -> io::Result<()> {
    let data_len = (samples.len() * 2) as u32;
    w.write_all(b"RIFF")?;
    w.write_all(&(36 + data_len).to_le_bytes())?;
    w.write_all(b"WAVEfmt ")?;
    w.write_all(&16u32.to_le_bytes())?;
    w.write_all(&1u16.to_le_bytes())?;
    w.write_all(&channels.to_le_bytes())?;
    w.write_all(&sample_rate.to_le_bytes())?;
    w.write_all(&(sample_rate * channels as u32 * 2).to_le_bytes())?;
    w.write_all(&(channels * 2).to_le_bytes())?;
    w.write_all(&16u16.to_le_bytes())?;
    w.write_all(b"data")?;
    w.write_all(&data_len.to_le_bytes())?;
    for s in samples {
        w.write_all(&((s.clamp(-1.0, 1.0) * 32767.0).round() as i16).to_le_bytes())?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    #[test]
    fn round_trips_16_bit() {
        let s: Vec<f32> = (0..100).map(|i| (i as f32 / 50.0) - 1.0).collect();
        let mut buf = Vec::new();
        super::write(&mut buf, 22050, 2, &s).unwrap();
        let w = super::read(&buf[..]).unwrap();
        assert_eq!((w.sample_rate, w.channels, w.samples.len()), (22050, 2, 100));
        for (a, b) in s.iter().zip(&w.samples) {
            assert!((a - b).abs() < 1e-4);
        }
    }
}
