# Arquitetura

## Visão geral

```
                ┌──────────────── Fontes de sinal ────────────────┐
  PC Windows    │ pc-bridge (Rust)     WASAPI loopback ─┐         │
                │                      MIDI clock ──────┤         │
  Telemóvel     │ phone-app (Kotlin+Rust)  mic / playback capture │
                └───────────────────────────┬─────────────────────┘
                     signals-core (Rust): FFT, bandas, beat, tempo, mood
                                            │  UDP binário ~60 Hz (PROTOCOLO-SIGNALS.md)
                                            ▼
┌────────────────────────────── tv-app (Android TV) ──────────────────────────────┐
│                                                                                 │
│  SignalHub ──► Signals (normalizados) ──► Mixer de reatividade ◄── Synth (LFO)   │
│      ▲  escolha da fonte (manual / auto por prioridade)       │   + Contexto    │
│      │                                                        ▼                 │
│  ImageProvider ──► textura + paleta ──────────────────► SceneRenderer           │
│  (cache em disco, prefetch)                               │  cena (.frag+.json) │
│                                                           │  drift, transições  │
│                                                           ▼                     │
│                                              OutputPass (perfil TV/Projetor)    │
│                                                           ▼                     │
│                                                         ecrã                    │
│                                                                                 │
│  ControlServer (HTTP mínimo) ◄──── control-web (telemóvel, por QR code)         │
│  DreamService (protetor de ecrã) + Activity (ecrã inteiro)                      │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Os cinco eixos (módulos plugáveis)

### 1. Fonte de sinal → `Signals`

Todas as fontes produzem o mesmo objeto normalizado (valores em 0..1 salvo indicação):

| Campo | Significado |
|---|---|
| `energy` | energia global, rápida |
| `low`, `mid`, `high` | energia por banda, rápida |
| `centroid` | brilho espectral |
| `beat` | envelope de beat (salta para 1 no onset, decai) |
| `beatPhase` | fase dentro do beat (0..1), **prevista** a partir do tempo para compensar latência |
| `bpm` | tempo estimado (0 se desconhecido) |
| `moodEnergy`, `moodBrightness` | versões muito suavizadas (janela 20–60 s) |

Fontes:
- **Nenhuma** — o `Synth` gera sinais a partir de LFOs e ruído lento.
- **PC** — `pc-bridge`, captura por loopback WASAPI.
- **Telemóvel (microfone)** — som da sala; precisa de AGC e de filtrar a voz/picos curtos.
- **Telemóvel (playback capture)** — `AudioPlaybackCapture` do Android 10+; consentimento por sessão; algumas apps bloqueiam.
- **MIDI clock** — via `pc-bridge` (`midir`), fornece `bpm` e `beatPhase` exatos.
- **Contexto** — hora do dia, nascer/pôr do sol, meteorologia; não substitui o áudio, **modula** paleta e temperatura.

**Escolha da fonte** na TV: manual (página de controlo) ou automática por prioridade configurável (ex.: PC → telemóvel → nenhuma), com timeout de silêncio/ausência de pacotes.

**Mixer de reatividade** (no host, não nas cenas): `sinalFinal = mix(sintético, real, reatividade)`. As cenas leem sempre sinais "vivos", independentemente da fonte.

### 2. Fonte de imagem → textura + paleta

`ImageProvider` com implementações por fonte (pasta local, AIC, Met, NASA, …). Responsabilidades:
- Pedir imagens **já no tamanho certo** (IIIF quando existir; ~1280 px no lado maior).
- **Cache em disco** com limite de tamanho (LRU) e **prefetch** em background (nunca na thread de render).
- **Extrair a paleta** (5 cores) uma vez por imagem, ao carregar.
- Funcionar offline com o que estiver em cache.
- Guardar os metadados de crédito/licença (para um overlay opcional).

Detalhes em `FONTES-IMAGEM.md`.

### 3. Cena

Shader GLSL ES 3.00 + esquema de parâmetros. Contrato completo em `FORMATO-CENA.md`. Por cima das cenas:
- **Drift**: cada parâmetro com `drift > 0` vagueia lentamente dentro do seu intervalo, à volta do valor definido pelo utilizador. Algoritmo exato em [Drift de parâmetros](#drift-de-parâmetros).
- **Transições** entre cenas (crossfade das duas saídas ao longo de N segundos).
- **Playlists/rotação** automática e **presets** (cena + valores de parâmetros + fonte de imagem).

#### Drift de parâmetros

Determinista e igual em todos os hosts (implementação de referência: `authoring/src/drift.ts`; casos partilhados: `authoring/test/fixtures/drift-cases.json`, a cumprir com tolerância 1e-9; tudo em `double`).

1. **Semente**: FNV-1a de 32 bits sobre `"<id da cena>/<id do parâmetro>"` (0 passa a 1).
2. **Três números** `u1, u2, u3` em [0, 1): três passos de xorshift32 (`x ^= x << 13; x ^= x >>> 17; x ^= x << 5`, em 32 bits sem sinal), cada um dividido por 2³².
3. **Espectro**: `f1 = 1 / (30 + 90·u1)` Hz (período de 30 a 120 s), `f2 = 2.618·f1`, `φ1 = 2π·u2`, `φ2 = 2π·u3`.
4. **Desvio** em [−1, 1]: `o(t) = 0.65·sin(2π·f1·t + φ1) + 0.35·sin(2π·f2·t + φ2)`.
5. **Valor**: `clamp(base + o(t) · 0.5 · drift · intensidade · (max − min), min, max)`; `int` arredonda depois; `bool` e `color` não derivam. Ou seja, `drift` é a largura total do vaguear como fração do intervalo.

`t` é o **tempo de drift** do host: soma de `dt × velocidade` desde o arranque (não reinicia ao trocar de cena; mudar a velocidade não dá saltos). `intensidade` (0 = desligado, 1 = como na cena) e `velocidade` (1 = tempo real) são definições globais do host.

### 4. Perfil de saída

Passe final (`shared/glsl/output.frag`) independente da cena: brilho máximo, contraste, saturação, gama, piso de preto, temperatura de cor, dithering. Também define `renderScale` e `maxFps` por defeito. Perfis em `shared/profiles/*.json`; ajustáveis em runtime pela página de controlo (o perfil do projetor afina-se quando ele for comprado).

### 5. Controlo

`ControlServer` dentro da `tv-app` (HTTP mínimo, sem framework pesado) serve a página `control-web` (HTML/JS/CSS estáticos, sem framework, poucos KB). A UI de parâmetros é **gerada a partir do `scene.json`**. QR code no ecrã com o endereço.

API (rascunho, a fixar na Fase 3):

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/` | página de controlo |
| GET | `/api/scenes` | lista de cenas com os respetivos esquemas |
| GET | `/api/state` | estado atual (cena, valores, fonte de sinal, fonte de imagem, perfil, reatividade) |
| PATCH | `/api/state` | alteração parcial do estado |
| GET | `/api/sources` | fontes de sinal visíveis e o seu estado |

## Renderer (tv-app)

- `GLSurfaceView` com `RENDERMODE_WHEN_DIRTY` + `Choreographer` para respeitar o `maxFps` da cena.
- A cena renderiza para um FBO à **escala interna**; o `OutputPass` lê esse FBO e desenha no ecrã à resolução nativa (escalamento bilinear).
- Cenas com `feedback: true` usam dois FBOs em ping-pong; preferir `RGBA16F` (exige `EXT_color_buffer_half_float` — verificar suporte), com fallback para `RGBA8`.
- **Escala dinâmica**: se o frame time médio exceder o orçamento, baixa a `renderScale` por degraus; sobe quando houver folga.
- Composição de shaders: o host junta `shared/glsl/header.glsl` + parâmetros declarados (`uniform float p_<id>;`) + corpo da cena com `#include` resolvidos (pré-processador trivial, implementado igual na `tv-app` e no `authoring`).
- Os ficheiros de `scenes/` e `shared/` são copiados para `assets/` por uma tarefa do Gradle no build — não se editam cópias.
- Overlay de diagnóstico (fps, frame time, renderScale, fonte de sinal) ligável pela página de controlo.

## Outros hosts (ideia, por decidir)

A `tv-app` é o primeiro host do renderer, não necessariamente o único. O dono do projeto quer poder gerar a imagem a partir de **qualquer dos três dispositivos** (box, telemóvel, PC) ligados ao projetor/TV, ou dentro do próprio projetor se este tiver sistema de apps. Para manter essa porta aberta:

- As cenas, a biblioteca GLSL e o passe de saída ficam **sem nada específico de Android** (já é regra: `FORMATO-CENA.md`).
- A lógica de host que não é render (drift, mixer de reatividade, `Synth`, escolha de fonte, composição de shaders) deve ficar em módulos sem dependências de UI, para poder ser portada ou partilhada.
- Caminhos prováveis: a `phone-app` ganha um modo "renderer" (mesmo código Kotlin/GLES da `tv-app`, ecrã espelhado ou saída HDMI/cast); no PC, um host nativo leve ou a própria página de autoria em ecrã inteiro; num projetor com Android TV, a `tv-app` tal como está.

Sem decisão nem ADR até haver projetor. Não antecipar abstrações por causa disto.

## Partilha de código

| Código | Usado por |
|---|---|
| `scenes/`, `shared/glsl/`, `shared/profiles/` | tv-app, authoring, control-web |
| `signals-core` (Rust) | pc-bridge (nativo Windows), phone-app (via NDK/JNI) |
| Especificação do pacote UDP | signals-core (emissor), tv-app (recetor), authoring (recetor via ponte WebSocket local, opcional) |

## Descoberta na rede

A `tv-app` envia um **beacon** UDP de 2 em 2 s (porto 47801, broadcast). Os emissores escutam o beacon, aprendem o IP da TV e passam a enviar **unicast** para o porto 47800. Evita broadcast a 60 Hz no Wi-Fi. Também é possível configurar o IP manualmente. Formato em `PROTOCOLO-SIGNALS.md`.
