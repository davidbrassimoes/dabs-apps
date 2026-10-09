# Repositório, comandos e ambiente

Saiu do `CLAUDE.md` do produto em 2026-10-06 (decisão 2 de `projects/santa-vibes.md`), para
este ficheiro curto ficar com o essencial. Ler quando a tarefa pede compilar, correr ou mexer
na estrutura.

## Mapa

```
docs/            documentação e ADRs
scenes/          cenas (fonte única): <id>/scene.frag + scene.json
shared/glsl/     cabeçalho de uniforms, biblioteca GLSL, passe de saída
shared/profiles/ perfis de saída (tv.json, projector.json)
authoring/       ferramenta de autoria no browser (só desenvolvimento) — Fase 0
tv-app/          app Android TV (Kotlin + GLES 3.0) — Fase 0/3
signals-core/    núcleo de DSP em Rust partilhado — Fase 1
pc-bridge/       bridge Windows (WASAPI loopback → UDP) em Rust — Fase 1
phone-app/       app Android emissora de sinais (mic / playback capture) — Fase 1
control-web/     página de controlo para o telemóvel, servida pela tv-app — Fase 3
```

## Comandos

> Preencher à medida que cada componente for criado. Verificação de tudo: `scripts/check`.

| Componente | Compilar | Correr | Testar |
|---|---|---|---|
| authoring | `cd authoring && npm install && npm run build` | `npm run dev` → http://localhost:5180/ | `npm test` (composição, drift, paleta) · `npm run check` (tipos) · `node scripts/smoke.mjs --gpu` (headless, com o servidor a correr) |
| tv-app | `cd tv-app && ./gradlew :app:assembleDebug` | `adb -s 192.168.1.27:5555 install -r app/build/outputs/apk/debug/app-debug.apk` + `adb -s 192.168.1.27:5555 shell am start -n dev.santavibes.tv/.MainActivity`; logs: `adb -s 192.168.1.27:5555 logcat -s SantaVibes` | `./gradlew :core:test` |
| signals-core | `cd signals-core && cargo build --release` | `cargo run --release --example analyze -- musica.wav` | `cargo test --release` · `cargo clippy --all-targets` (Rust via `rustup`; fora do `scripts/check` por agora) |
| pc-bridge | `cd pc-bridge && cargo build --release` | `target/release/pc-bridge.exe [--tv 192.168.1.27]`; sem TV: `cargo run --release --example fake_tv` | `cargo clippy --all-targets`; teste manual com o `fake_tv` (README) |
| phone-app | `cd phone-app && ./gradlew :app:assembleDebug` (precisa de NDK 30, targets Android do Rust e `cargo-ndk`: README) | `adb install -r app/build/outputs/apk/debug/app-debug.apk` | `cd native && cargo test --release`; manual no emulador ou telemóvel (README) |

O servidor do authoring é `node dev-server.mjs` (não é Vite). Fixtures partilhadas entre o
authoring e a tv-app: `authoring/test/fixtures/`; a de paleta gera-se com
`node authoring/scripts/palette-cases.mjs`.

## Convenções

- Documentação em português de Portugal; código, identificadores, comentários e commits em inglês.
- Commits pequenos e descritivos (Conventional Commits: `feat:`, `fix:`, `docs:`, `perf:`, `refactor:`, `chore:`).
- Cada componente tem o seu `README.md` com: como compilar, como correr, como testar.
- Decisões relevantes → novo ficheiro em `docs/decisoes/NNNN-titulo.md`.

## Ambiente do dono

- **PC**: Windows (corre o Claude Code, a ferramenta de autoria e a bridge de áudio).
- **Box**: Google TV Streamer, Android 14 (API 34), GPU PowerVR GE9215 com GLES 3.2, surface 1920×1080 na TV atual (detalhes em `tv-app/README.md`). Ethernet em `192.168.1.27`; `adb connect 192.168.1.27:5555`. Instalação por sideload via `adb`. Se estiver em standby (`dumpsys power` → `mWakefulness=Asleep`), a app fica parada: `adb shell input keyevent KEYCODE_WAKEUP`.
- **Telemóvel**: Android.
- **Projetor**: ainda por comprar — o perfil "Projetor" deve ser ajustável sem recompilar.
- O dono testa na box física; quando uma tarefa precisar de validação no hardware, diz exatamente o comando a correr e o que observar (fps, frame time, aquecimento).
