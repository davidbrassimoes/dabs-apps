---
name: santa-vibes-scene
description: Write a new scene for Santa Vibes (scenes/<id>/scene.frag + scene.json) from an idea, a mood or a reference (an artist, a material, "chuva num vidro", "Turrell"), check it in the authoring and measure it on the Google TV box, then add it to the screens gallery. Use whenever DABS asks for a new scene, a new look, "mais cenas", a variation of an existing scene that needs new code, or a scene in the style of something — even when he does not say "skill" or "shader". Not for presets of an existing scene (that is the preset lab in the authoring).
---

# Santa Vibes — writing a scene

A scene is the light of a room for a whole evening, on a TV now and a weak projector later.
It wins by being beautiful at a glance and never tiring: slow, large shapes, rich gradients,
mid luminance. It loses by detail, flicker, black areas or by stuttering on the box. Work
from `products/santa-vibes/` and read only what is listed here.

## Read first (once per session)

1. `docs/FORMATO-CENA.md`: the contract (format 4), the params, presets and the `react`
   convention. It is the law: a scene uses only the contract uniforms and its own params.
2. One existing scene close to the idea, both files (`scenes/halo/` is the simplest;
   `scenes/nebula/` uses noise and an image; `scenes/ink-bloom/` uses feedback).
3. `shared/glsl/lib/`: `palette()`, `tri()`, `luma()` (color), `vnoise`/`fbm` (noise, reads the
   `uNoise` texture), `groove()` (react), `imageCoverUV()` (image).

## Decide before writing

Write these down in the reply to DABS, in a few lines, before code:

- **One sentence**: what the room looks like with it on. If it needs two sentences, it is two scenes.
- **Style** (first tag): `ambient`, `psicadélico clássico`, `psicadélico tech`, `glitch` or
  `geométrico`. It groups the scene in the authoring, the control page and the gallery.
- **Not a duplicate**: say which existing scene is closest and why this one is different.
  `liquid-light` already does metaballs, `halo` a Turrell/Rothko orb, `aurora` auroras.
- **Image**: `none`, `optional` (works the same without one; may use only the palette) or `required`.
- **Feedback**: only when the look truly needs the previous frame (trails, ink). The output of
  a feedback scene is both what is shown and its own state: no hidden channels.

Ask DABS only when the idea is ambiguous between two very different scenes. Otherwise decide.

## Writing it

`scene.frag` (GLSL ES 3.00, no `#version`, starts with a comment saying what it is and what it
costs) and `scene.json` (`"format": 4`). Rules learned on the box (GPU PowerVR GE9215):

- **Light first.** Most of the screen sits between `palette(0.2)` and `palette(0.8)`; no large
  black areas, no pure white. Colours come from `palette()`, never hard-coded, so presets,
  images and palette rotation work.
- **Slow.** Motion in tens of seconds. `uTime * p_speed * small` and `tri()` for endless drift.
  No flicker: nothing that changes faster than ~4 Hz.
- **Cheap.** Target ≤ 25 ms per frame at its `renderScale` on the box (the output pass costs
  9 ms of the 33 ms budget). At most 3–4 `fbm` octaves; no loops over many items per pixel;
  `renderScale` 0.33 for noise-heavy scenes, 0.5 for simple ones, lower for feedback.
- **Reactivity, two params, always last, group `Reatividade`:**
  - `pulse` (float 0..1, default ~0.3, drift 0): how much the current look breathes with `uBeat.x`.
  - `react` (float 0..1, default 0, drift 0, label "Reação à música"): the reactive look. The host
    forces it to 0 or 1. With 1, the beat and the bass change size, shape or colour, through
    `groove()` (smooth, peaks on the beat), never `uBeat.x` jumps.
    `float beat = mix(p_pulse * uBeat.x, p_pulse * 4.0 * groove(), p_react);` is the usual start.
- **Params**: 4–6 that change the look a lot (speed, scale, shape, softness…), each with a
  sensible range and `drift` (0.1–0.25 for shape, 0 for anything that should stay put).
  Labels in Portuguese (PT-PT).
- **Presets**: 2–3 named ones that look clearly different (palette and a few params). The
  default must already be the best of them.
- **Projector**: big shapes, gradients, saturated mid tones. Fine lines vanish on a projector.

## Checking it

In order; stop and fix at the first failure.

1. `cd authoring && npm test` (validates every scene.json and preset) and `npm run check`.
2. `cd tv-app && ./gradlew :core:test` (the box composes and parses every scene).
3. Look at it: `npm run dev` in `authoring`, then `node scripts/smoke.mjs --gpu --quick --only <id> --presets --settle 8`
   saves `<id>.png` and `<id>@<preset>.png` in `%TEMP%\santa-vibes-smoke`. Look at every capture:
   no black areas, colours from the palette, presets distinct. Also with the reactive look
   (set `react` to 1 in the panel) and, for `image: optional`, with an image dropped on the page.
4. On the box (`tv-app/README.md` for install): build, install, then
   `adb -s 192.168.1.27:5555 shell am start -n dev.santavibes.tv/.MainActivity --ez bench true --es scenes <id> --es scales 0.25,0.33,0.5`
   and read the `bench` lines in `logcat -s SantaVibes`. Pick the largest scale under ~25 ms and
   put it in `renderScale`. Then open the scene normally (`--es scene <id>`) for 60 s and check
   the `stats` line: fps at the cap, `alloc` 0 with the overlay hidden.
   If the box is asleep: `adb -s 192.168.1.27:5555 shell input keyevent KEYCODE_WAKEUP`.
5. Screenshot it on the box (`adb exec-out screencap -p > shot.png`) and look at it.

## Finishing

- Rebuild the screens gallery (`cd authoring && npm run gallery -- --out <scratchpad>/gallery.html`)
  and republish it to https://claude.ai/artifact/4BWzrYabkCxiG5hrytY62L.
- `NOTES.md` of the product: one entry with the idea, the bench numbers and what DABS has not seen.
- `scripts/check santa-vibes` from the root, then commit (`feat(santa-vibes): <id> scene`) on a branch.
- Tell DABS what the scene is, the numbers, and the one thing to look at on the TV.
