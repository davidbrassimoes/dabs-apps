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
  - Resolvido em 2026-10-07 (formato 4): `flow-particles` avança e desvanece por segundo (`uDeltaTime`); `image: optional` quer dizer "pode usar a imagem, nem que seja só a paleta", por isso o `color-field` fica como está.
- [x] **0.4 Renderer nativo mínimo (`tv-app/`)** (2026-10-05; validado na box: as 15 cenas compilam no driver PowerVR e coincidem com a autoria; RGBA16F; zero alocações no loop de render; ver `tv-app/README.md`) — Kotlin + GLES 3.0:
  - Activity em ecrã inteiro, carrega cenas de `assets/` (cópia gerada pelo Gradle a partir de `scenes/` e `shared/`),
  - FBO à escala interna + `OutputPass`, limite de fps, feedback ping-pong,
  - troca de cena com o comando (D-pad esquerda/direita),
  - overlay de diagnóstico,
  - **zero alocações por frame**.
- [x] **0.5 Medição na box** (2026-10-05; tudo em `tv-app/README.md`: modo benchmark; ruído por textura (formato v3) e passe de saída otimizado — cenas ~2,2× e saída 15,7 → 9,4 ms; `renderScale` por cena pelos números; as 15 cenas no limite de fps; 31 min de `nebula` a 30,0 fps sem throttling; escala dinâmica não precisa por agora)
- [x] **0.6 Drift e transições** (authoring 2026-10-05; tv-app 2026-10-06, validado na box: drift e paleta iguais ao authoring pelas fixtures partilhadas, crossfade nas 15 cenas, sem alocações por frame, compilação das cenas no arranque; custo do crossfade em `tv-app/README.md`. **Por decidir:** a cena que sai continua a ser renderizada e 6 das 15 transições caem para 24–26 fps durante a mistura; renderizá-la a meio ritmo ou parada resolve, mas muda o que diz `ARQUITETURA.md` — ver `projects/santa-vibes.md`) — drift de parâmetros e crossfade entre cenas (no authoring e na tv-app). Incluir **variação de paleta** no host (pedido do dono, 2026-10-05): cada cena pode ter várias paletas em `scene.json` e o host roda entre elas devagar, ou deriva o matiz; as cenas continuam a ler só `uPalette`.

**Critérios de aceitação:**
- As 3 cenas correm na box a ≥ 30 fps estáveis com a `renderScale` por defeito, durante 30 min, sem engasgos visíveis. _(cumprido 2026-10-05: as 15 cenas no limite; 31 min na `nebula`, a mais pesada)_
- Uma alteração a um `.frag` aparece no authoring em < 1 s. _(medido 2026-10-04: 140–190 ms, `authoring/scripts/smoke.mjs`)_
- Os mesmos ficheiros de cena produzem o mesmo resultado (visualmente) no authoring e na box.

---

## Fase 1 — Reatividade

**Objetivo:** a imagem reage à música do PC e ao telemóvel.

- [x] **1.1 `signals-core` (Rust)** (2026-10-06; ver `signals-core/README.md`: 24 testes com áudio gerado e WAV, zero alocações, ~18 µs por frame; afinação com música real fica para 1.2/1.5) — FFT (`rustfft`), bandas, envelope de beat, deteção de onsets, estimativa de tempo e **previsão de fase do beat**, suavizações "mood", AGC. Serialização do pacote (`PROTOCOLO-SIGNALS.md`). Testes com ficheiros WAV de referência.
- [x] **1.2 `pc-bridge` (Rust, Windows)** (2026-10-06; ver `pc-bridge/README.md`: 60 pkt/s sem perdas, 1,6 % de um núcleo, 11,9 MB; pacotes chegam à box; por fazer: arranque automático e ícone) — captura por loopback WASAPI (`cpal`), escuta do beacon, envio unicast a ~60 Hz. Ícone na área de notificação (opcional) ou serviço discreto. (O MIDI clock que estava aqui saiu do produto: ADR 0003.)
- [x] **1.3 Receção na `tv-app`** (2026-10-06; ver `tv-app/README.md`: descoberta pelo beacon, 60 pkt/s sem alocações, regresso ao Synth em ≤ 3 s; falta afinar a reação a olho) — `SignalHub` (thread própria, socket UDP, sem alocações por pacote), beacon, escolha da fonte (manual/automática), mixer de reatividade com o `Synth`.
- [x] **1.4 `phone-app` (Android)** (2026-10-07: testada num Pixel 8 até à box, microfone e som de uma app; o tempo pelo microfone fica para a afinação — ver `phone-app/README.md`) — UI mínima: escolher microfone ou playback capture, estado da ligação. Foreground service do tipo `microphone`/`mediaProjection`. `signals-core` via NDK (`cargo-ndk`) + JNI.
- [x] **1.5 Afinação da latência** (comparar o beat visto na TV com o ouvido na sala). 2026-10-08: `pc-bridge` a -10 ms, confirmado com cliques a 115 e 73 BPM e com rock (erro -13 a +20 ms, menos de um frame; `pc-bridge/README.md`). O MIDI clock que estava neste passo saiu do produto (ADR 0003, 2026-10-07).
- [x] **1.6 Ponte opcional para o authoring** (2026-10-08: `pc-bridge` serve os pacotes em `ws://127.0.0.1:47810`; o authoring mistura-os com um port do mixer da TV, verificado frame a frame) — o authoring recebe os sinais reais (WebSocket local servido pela bridge), para afinar cenas reativas no PC.

**Critérios de aceitação:**
- Com música no PC, a cena responde de forma visível e agradável; com reatividade a 0 fica indistinguível do modo sem sinal.
- Desligar o PC a meio: a TV volta ao sintético em ≤ 3 s, sem saltos bruscos.
- `pc-bridge` usa < 2 % de CPU e < 20 MB de RAM.

---

## Fase 2 — Fontes de imagem e cenas híbridas

- [x] **2.1 `ImageProvider`** (2026-10-08: pasta de casa por HTTP, cache LRU 300 MB, paleta por k-means, troca com fade, offline pela cache; ver `tv-app/README.md`) na tv-app: pasta local (na box e/ou partilha do PC; candidato: pasta servida por HTTP no servidor de casa, ver `NOTES.md` 2026-10-06), cache em disco LRU, prefetch em background, extração de paleta.
- [x] **2.2 Primeiro acervo open access** (2026-10-08: The Met; a AIC, proposta, tem as imagens atrás de um desafio Cloudflare — `FONTES-IMAGEM.md`) — com filtros (domínio público, tipo de obra, cores dominantes) e metadados de crédito.
- [x] **2.3 Cenas híbridas** (2026-10-08: `melt-painting` "Pintura derretida" e `light-mosaic` "Mosaico de luz", `image: optional`, funcionam também sem imagem; 30 fps na box) — pelo menos 2 cenas novas que usam `uImage` e `uPalette` (ex.: pintura a "derreter" em campo de luz; partículas guiadas pela imagem).
- [x] **2.4 Mais fontes** (2026-10-08: NASA Image Library, sem chave; outras fontes em `FONTES-IMAGEM.md` por verificar) conforme `FONTES-IMAGEM.md`.

**Critérios de aceitação:** troca de imagem sem engasgo no render; funciona offline só com a cache.

---

## Fase 3 — Produto na box

- ~~**3.1 `DreamService`**~~ — retirado a 2026-10-09 (DABS, decisão 17): não serve o uso do `VISAO.md`. Fica o `Player`, que o 3.2 usa.
- [x] **3.2 `ControlServer` + `control-web`** — página no telemóvel por QR code; UI gerada dos esquemas; escolha de cena, parâmetros, fonte de sinal, fonte de imagem, perfil, reatividade, brilho/temperatura. Feito em 2026-10-09 (maqueta aprovada; decisão 18).
- [x] **3.3 Presets e playlists** — guardar/carregar; rotação automática com transições. Feito em 2026-10-09: presets do utilizador na box, exportar em JSON, rotação pelos presets (ou pelas cenas com menos de dois).
- [x] **3.4 Persistência** do estado na box. Feito em 2026-10-09 (`control-state.json` e `user-presets.json` em `files/`).
- [ ] **3.5 Perfil Projetor** afinado no projetor real (quando existir).

---

## Fase 4 — Extras

- [ ] Contexto: hora do dia (temperatura de cor circadiana), nascer/pôr do sol, meteorologia. Na prateleira (decisão 19).
- [ ] Interação pelo telemóvel (touchpad que injeta forças, acelerómetro). Na prateleira (decisão 19).
- [ ] Mais cenas e estéticas (auroras, reaction-diffusion, metaballs, campos tipo Turrell…).
- [ ] Imagens geradas por IA no PC. Na prateleira (decisão 19).
- [ ] Luzes inteligentes da sala sincronizadas com a paleta. Na prateleira (decisão 19; DABS não tem lâmpadas).
