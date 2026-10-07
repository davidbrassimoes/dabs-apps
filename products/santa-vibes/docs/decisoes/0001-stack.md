# 0001 — Stack tecnológico

- **Estado:** aceite
- **Data:** 2026-10-04

## Contexto

O critério principal é **desempenho, eficiência e leveza**. A preferência de linguagem do dono do projeto não conta (o desenvolvimento é feito com Claude Code). A box (Google TV Streamer) tem GPU modesta e vai estar ligada horas seguidas.

## Decisão

| Peça | Escolha |
|---|---|
| Renderer na box | Kotlin + OpenGL ES 3.0 nativo (`GLSurfaceView`), sem framework de UI no caminho de render |
| Shaders | GLSL ES 3.00 (idêntico ao WebGL2) |
| Ferramenta de autoria | Página WebGL2 + TypeScript, só para desenvolvimento |
| Núcleo de DSP | Rust (`signals-core`), partilhado entre PC e telemóvel |
| Bridge do PC | Rust (`cpal` WASAPI loopback, `rustfft`) — binário único, sem runtime. (`midir` saiu com o MIDI clock: ADR 0003) |
| App do telemóvel | Kotlin (captura, serviço, UI mínima) + `signals-core` via NDK/JNI |
| Sinais | UDP binário fixo, ~60 Hz, descoberta por beacon |
| Controlo | HTTP mínimo dentro da tv-app + página HTML/JS sem framework |

## Alternativas rejeitadas

- **WebView na box (motor web empacotado):** reaproveitamento total de código, mas acrescenta um motor JS e a camada da WebView numa GPU modesta. Mantém-se só para autoria.
- **.NET MAUI / C#:** runtime pesado para o que se pretende; navegação por foco em TV difícil.
- **Rust + wgpu na box:** partilha de código com o desktop, mas toolchain mais pesado, binário maior e integração com `DreamService` exige cola em Java/Kotlin de qualquer forma. Ganho de desempenho nulo para fragment shaders em ecrã inteiro.
- **Vulkan:** o custo está no fragment shader (fill rate), não no overhead da API. Complexidade sem ganho visível.
- **Análise de áudio na TV:** a música não toca na box e o Android restringe a captura de outras apps. A análise fica nos emissores.
- **JSON/WebSocket para sinais a 60 Hz:** desperdício de CPU e banda; UDP binário é mais simples e mais leve.

## Consequências

- Os shaders são a fonte única e correm igual no browser e na box.
- O DSP é escrito uma vez (Rust) e testado com ficheiros WAV.
- Se a GPU da box não chegar, a mitigação é a escala interna/dinâmica e o limite de fps, não mudar de stack.
