"""Santa Vibes latency check (step 1.5): is the TV's beat pulse in time with the room's sound?

1. Click track (4 s silence, then clicks at 115 BPM; not 120, which lands on the same frame at 30 fps):
     ffmpeg -f lavfi -i "aevalsrc='if(lt(t,4),0,0.9*sin(2*PI*60*mod(t-4,60/115))*exp(-40*mod(t-4,60/115))
       +0.5*sin(2*PI*2000*mod(t-4,60/115))*exp(-300*mod(t-4,60/115)))':s=48000:d=70" -ac 2 clicks115.wav
2. TV: scene halo, reactive look, overlay hidden. A phone films the TV and hears the PC's speakers;
   play clicks115.wav on the PC while it records.
3. uv run --no-project --with numpy python -I latency.py <phone.mp4> <crop w:h:x:y of the TV>
   with env FFMPEG, FFPROBE (paths) and CLICKS (the wav). Positive delay_ms = the picture is late.
   New --lookahead = old - delay_ms.
   Real music: any clip with 4 s of silence first; BPM and BEAT0 (offset of its first beat) from the clip.
"""
import json
import os
import subprocess
import sys

import numpy as np

FF, FP = os.environ["FFMPEG"], os.environ["FFPROBE"]
path, crop = sys.argv[1], sys.argv[2]


def run(cmd):
    return subprocess.run(cmd, capture_output=True, check=True).stdout


# --- audio: locate the whole click track in the phone recording by cross-correlation
CLICKS = os.environ["CLICKS"]  # the WAV that was played: 4 s silence, then clicks every 60/BPM s
BPM = float(os.environ.get("BPM", "115"))
BEAT0 = float(os.environ.get("BEAT0", "0"))  # seconds from the clip's start (after the 4 s) to its first beat
sr = 16000


def load(p, extra=()):
    return np.frombuffer(run([FF, "-loglevel", "error", "-i", p, *extra, "-ac", "1", "-ar", str(sr), "-f", "f32le", "-"]),
                         np.float32).astype(np.float64)


v = load(path, ("-map", "0:a:0"))
c = load(CLICKS)
nfft = 1 << int(np.ceil(np.log2(len(v) + len(c))))
r = np.fft.irfft(np.fft.rfft(v, nfft) * np.conj(np.fft.rfft(c, nfft)), nfft)[: len(v)]
lag = int(np.argmax(np.abs(r)))
xc_ratio = float(np.abs(r[lag]) / np.median(np.abs(r)))
a_start = float(run([FP, "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=start_time",
                     "-of", "default=nw=1:nk=1", path]).decode().split()[0])
T = 60.0 / BPM
t0 = a_start + lag / sr + 4.0 + BEAT0  # first beat as heard by the phone, in video time
n_clicks = int((len(c) / sr - 4.0) / T)
on = t0 + T * np.arange(n_clicks)
keep = np.ones(n_clicks, bool)

# --- video
w, h, x, y = crop.split(":")
raw = run([FF, "-loglevel", "error", "-i", path, "-map", "0:v:0", "-fps_mode", "passthrough",
           "-vf", f"crop={w}:{h}:{x}:{y},scale=32:18,format=gray", "-f", "rawvideo", "-"])
lum = np.frombuffer(raw, np.uint8).reshape(-1, 18 * 32).mean(axis=1)
pts = np.array([float(v.strip(",")) for v in run([FP, "-v", "error", "-select_streams", "v:0", "-show_entries",
                                       "frame=pts_time", "-of", "csv=p=0", path]).decode().split()])
m = min(len(pts), len(lum))
pts, lum = pts[:m], lum[:m]

sel = (pts > on[keep][0] + 4) & (pts < on[keep][-1])  # skip the first beats: tempo lock
tt, ll = pts[sel], lum[sel]
k2 = 15  # remove slow drift (~0.5 s moving average at 30 fps)
ll = ll - np.convolve(ll, np.ones(k2) / k2, mode="same")
ph = ((tt - t0) / T) % 1.0


def peak_delay(phase, val):
    z = np.sum(val * np.exp(-2j * np.pi * phase))
    p = (-np.angle(z) / (2 * np.pi)) % 1.0  # phase where the first harmonic peaks
    if p > 0.5:
        p -= 1.0
    return p * T, abs(z) / len(val)


delay, amp = peak_delay(ph, ll)
blocks = []
beat_idx = np.floor((tt - t0) / T)
for b0 in np.arange(beat_idx.min(), beat_idx.max(), 8):
    s = (beat_idx >= b0) & (beat_idx < b0 + 8)
    if s.sum() > 60:
        blocks.append(peak_delay(ph[s], ll[s])[0])
blocks = np.array(blocks)
bins = [float(ll[(ph >= i / 16) & (ph < (i + 1) / 16)].mean()) for i in range(16)]
print(json.dumps({
    "file": os.path.basename(path), "clicks": n_clicks, "xcorr_peak_ratio": round(xc_ratio, 1), "first_click_s": round(t0, 3), "frames": int(sel.sum()),
    "delay_ms": round(1000 * delay, 1), "block_ms_mean": round(1000 * blocks.mean(), 1),
    "block_ms_sd": round(1000 * blocks.std(), 1), "n_blocks": len(blocks),
    "pulse_amp": round(float(amp), 2), "fold": [round(b, 2) for b in bins],
}))
