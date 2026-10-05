# Roadmap

Legenda: `[ ]` por fazer · `[~]` em curso · `[x]` feito. Atualizar este ficheiro no fim de cada passo.

---

## Fase 0 — Motor e primeiras cenas

**Objetivo:** iterar cenas rapidamente no PC e validar o desempenho real na box desde o primeiro dia.

- [x] **0.1 Repo base** (2026-10-04; dados da box em `tv-app/README.md`) — `git init`, `.gitignore`, verificação do ambiente (Node, JDK, Android SDK, adb ligado à box). Confirmar versão do Android TV na box.
- [x] **0.2 Ferramenta de autoria (`authoring/`)** (2026-10-04; ver `authoring/README.md`) — página WebGL2 só para desenvolvimento:
  - lê `scenes/*` e `shared/*` diretamente (servidor de dev leve, ex.: Vite sem framework),
  - pré-processador `#include` + composição de cabeçalho/parâmetros igual ao da tv-app,
  - **hot reload** de `.frag` e `.json`,
  - painel de parâmetros **gerado do `scene.json`**, seletor de cena, seletor de perfil (TV/Projetor), sinais sintéticos com sliders manuais (para simular reatividade sem a bridge),
  - suporte a `feedback` (ping-pong) e `renderScale`,
  - contador de fps/frame time.
- [x] **0.3 Afinar as cenas iniciais** (2026-10-05; ficam todas as 15 por agora — a seleção final faz-se mais tarde, com testes a sério; a decisão sobre partículas reais em `flow-particles` passa para depois da medição na box, 0.5) — às 3 originais juntaram-se 4 propostas de estética (2026-10-05): `liquid-light` (óleo e água em retroprojetor), `ink-bloom` (tinta em água, feedback com difusão), `aurora` (cortinas de luz), `halo` (orbe à Turrell), `marble` (mármore iridescente), `breath` (gradiente que respira, sem forma), `veils` (véus de seda); e, para cobrir os estilos pedidos pelo dono (psicadélico clássico, psicadélico tech, glitch, ambient, geométrico), `spiral-melt`, `plasma`, `glitch-drift`, `geo-tiles`, `bars`. O estilo é a primeira tag de cada cena. Decidir quais ficam. Afinar (`color-field`, `nebula`, `flow-particles`) até ficarem bonitas. Decidir se `flow-particles` passa a sistema de partículas real (estado em textura + pontos instanciados) ou se fica como trilhos por feedback — medir na box.
  - [x] Presets por cena (2026-10-05): formato de cena v2 com `presets` opcional (`FORMATO-CENA.md`); seletor de preset, editor de paleta e "Copiar como preset" no authoring; 2–3 presets em cada uma das 15 cenas; `smoke.mjs --presets` captura todos.
  - [x] Defeitos vistos nas capturas, corrigidos (2026-10-05) e validados no Chrome: costura do `spiral-melt` (descontinuidade do `atan` → número inteiro de pares de braços, com cross-fade); `flow-particles` quase preto e dependente da resolução (emissão em unidades normalizadas, fade para fundo da paleta; igual a `renderScale` 0.25 e 1.0); `nebula` escura (piso de brilho) e a esticar a imagem (`imageCoverUV` em `shared/glsl/lib/image.glsl`).
  - Por fazer: `flow-particles` avança e desvanece por frame (não por segundo), por isso depende do limite de fps — aceitável com fps fixo, rever se o contrato ganhar `uDeltaTime`. `color-field` declara `image: optional` mas não lê `uImage`.
- [ ] **0.4 Renderer nativo mínimo (`tv-app/`)** — Kotlin + GLES 3.0:
  - Activity em ecrã inteiro, carrega cenas de `assets/` (cópia gerada pelo Gradle a partir de `scenes/` e `shared/`),
  - FBO à escala interna + `OutputPass`, limite de fps, feedback ping-pong,
  - troca de cena com o comando (D-pad esquerda/direita),
  - overlay de diagnóstico,
  - **zero alocações por frame**.
- [ ] **0.5 Medição na box** — as 3 cenas a correr; registar frame time por cena e por `renderScale` numa tabela em `tv-app/README.md`. Implementar escala dinâmica se necessário.
- [~] **0.6 Drift e transições** (authoring feito em 2026-10-05: drift determinista com casos partilhados, variação de paleta em OKLab, crossfade entre cenas; falta a tv-app, depois do 0.4) — drift de parâmetros e crossfade entre cenas (no authoring e na tv-app). Incluir **variação de paleta** no host (pedido do dono, 2026-10-05): cada cena pode ter várias paletas em `scene.json` e o host roda entre elas devagar, ou deriva o matiz; as cenas continuam a ler só `uPalette`.

**Critérios de aceitação:**
- As 3 cenas correm na box a ≥ 30 fps estáveis com a `renderScale` por defeito, durante 30 min, sem engasgos visíveis.
- Uma alteração a um `.frag` aparece no authoring em < 1 s. _(medido 2026-10-04: 140–190 ms, `authoring/scripts/smoke.mjs`)_
- Os mesmos ficheiros de cena produzem o mesmo resultado (visualmente) no authoring e na box.

---

## Fase 1 — Reatividade

**Objetivo:** a imagem reage à música do PC e ao telemóvel.

- [ ] **1.1 `signals-core` (Rust)** — FFT (`rustfft`), bandas, envelope de beat, deteção de onsets, estimativa de tempo e **previsão de fase do beat**, suavizações "mood", AGC. Serialização do pacote (`PROTOCOLO-SIGNALS.md`). Testes com ficheiros WAV de referência.
- [ ] **1.2 `pc-bridge` (Rust, Windows)** — captura por loopback WASAPI (`cpal`), escuta do beacon, envio unicast a ~60 Hz. Ícone na área de notificação (opcional) ou serviço discreto. MIDI clock via `midir` (pode ficar para 1.5).
- [ ] **1.3 Receção na `tv-app`** — `SignalHub` (thread própria, socket UDP, sem alocações por pacote), beacon, escolha da fonte (manual/automática), mixer de reatividade com o `Synth`.
- [ ] **1.4 `phone-app` (Android)** — UI mínima: escolher microfone ou playback capture, estado da ligação. Foreground service do tipo `microphone`/`mediaProjection`. `signals-core` via NDK (`cargo-ndk`) + JNI.
- [ ] **1.5 MIDI clock** e afinação da latência (comparar beat previsto vs. real).
- [ ] **1.6 Ponte opcional para o authoring** — o authoring recebe os sinais reais (WebSocket local servido pela bridge), para afinar cenas reativas no PC.

**Critérios de aceitação:**
- Com música no PC, a cena responde de forma visível e agradável; com reatividade a 0 fica indistinguível do modo sem sinal.
- Desligar o PC a meio: a TV volta ao sintético em ≤ 3 s, sem saltos bruscos.
- `pc-bridge` usa < 2 % de CPU e < 20 MB de RAM.

---

## Fase 2 — Fontes de imagem e cenas híbridas

- [ ] **2.1 `ImageProvider`** na tv-app: pasta local (na box e/ou partilha do PC), cache em disco LRU, prefetch em background, extração de paleta.
- [ ] **2.2 Primeiro acervo open access** (proposta: Art Institute of Chicago, via IIIF) — com filtros (domínio público, tipo de obra, cores dominantes) e metadados de crédito.
- [ ] **2.3 Cenas híbridas** — pelo menos 2 cenas novas que usam `uImage` e `uPalette` (ex.: pintura a "derreter" em campo de luz; partículas guiadas pela imagem).
- [ ] **2.4 Mais fontes** (NASA, Met, …) conforme `FONTES-IMAGEM.md`.

**Critérios de aceitação:** troca de imagem sem engasgo no render; funciona offline só com a cache.

---

## Fase 3 — Produto na box

- [ ] **3.1 `DreamService`** — a app aparece como protetor de ecrã do sistema.
- [ ] **3.2 `ControlServer` + `control-web`** — página no telemóvel por QR code; UI gerada dos esquemas; escolha de cena, parâmetros, fonte de sinal, fonte de imagem, perfil, reatividade, brilho/temperatura.
- [ ] **3.3 Presets e playlists** — guardar/carregar; rotação automática com transições.
- [ ] **3.4 Persistência** do estado na box.
- [ ] **3.5 Perfil Projetor** afinado no projetor real (quando existir).

---

## Fase 4 — Extras

- [ ] Contexto: hora do dia (temperatura de cor circadiana), nascer/pôr do sol, meteorologia.
- [ ] Interação pelo telemóvel (touchpad que injeta forças, acelerómetro).
- [ ] Mais cenas e estéticas (auroras, reaction-diffusion, metaballs, campos tipo Turrell…).
- [ ] Imagens geradas por IA no PC.
- [ ] Luzes inteligentes da sala sincronizadas com a paleta.
