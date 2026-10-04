# tv-app — app Android TV (Fase 0.4 e Fase 3)

Kotlin + OpenGL ES 3.0 nativo. Renderer de cenas, receção de sinais, fontes de imagem, servidor de controlo e `DreamService`.

Regras: ver `CLAUDE.md` (zero alocações por frame, sem WebView, dependências justificadas).

- `scenes/` e `shared/` são copiados para `assets/` por uma tarefa do Gradle — não editar `assets/` à mão.
- Instalação: `adb connect <ip-da-box>:5555` e depois `adb install -r <apk>`.

## A box (medido em 2026-10-04, passo 0.1)

| Item | Valor |
|---|---|
| Modelo | Google TV Streamer (`kirkwood`) |
| Android | 14 (API 34), build `UTTK.260317.003` |
| SoC / GPU | MediaTek MT8696, PowerVR Rogue GE9215, OpenGL ES 3.2 (driver 23.2) |
| RAM | 4 GB |
| Surface | 1920×1080 @ 60 Hz com a TV atual (Samsung 1080p); a box suporta 4K com um ecrã 4K |
| Rede | Ethernet, IP 192.168.1.27 (fixar no router); adb em `192.168.1.27:5555` |
| Por confirmar no 0.4 | `EXT_color_buffer_half_float` (FBO RGBA16F para cenas `feedback`) |

Consequência: `renderScale` é relativo à surface real. Com esta TV, 0.5 significa 960×540. A ferramenta de autoria simula a resolução da surface (1080p por defeito) para que a imagem seja comparável.

## Medições na box

| Cena | renderScale | fps alvo | frame time médio | frame time p95 | Notas |
|---|---|---|---|---|---|
| _a preencher no passo 0.5_ | | | | | |

> Por implementar.
