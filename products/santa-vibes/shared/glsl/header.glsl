#version 300 es
// Santa Vibes scene contract v3 — see docs/FORMATO-CENA.md
// Prepended by the host to every scene. Scene files must NOT contain #version.
precision highp float;
precision highp int;

uniform float uTime;          // seconds since scene start
uniform vec2  uResolution;    // render target size (renderScale applied)

uniform vec4  uBands;         // low, mid, high, energy (fast)
uniform vec4  uMood;          // moodEnergy, moodBrightness, centroid, reserved (slow)
uniform vec4  uBeat;          // envelope, phase, bpm, onset

uniform vec3  uPalette[5];    // current palette, ordered dark -> light

uniform sampler2D uImage;     // current image (scene.json "image" != "none")
uniform vec4  uImageInfo;     // hasImage, aspect, transitionMix, reserved

uniform sampler2D uPrev;      // previous frame of this scene ("feedback": true)

uniform sampler2D uNoise;     // 256x256 R8 random lattice, REPEAT + LINEAR (host-generated, v3)

out vec4 fragColor;
