# tv-app — app Android TV (Fase 0.4 e Fase 3)

Kotlin + OpenGL ES 3.0 nativo. Renderer de cenas, receção de sinais, fontes de imagem, servidor de controlo e `DreamService`.

Regras: ver `CLAUDE.md` (zero alocações por frame, sem WebView, dependências justificadas).

- `scenes/` e `shared/` são copiados para assets **gerados** em `app/build/` pela tarefa `copySceneAssets` em cada build. Nunca há cópias em `src/`.
- Sem dependências de runtime (ADR `docs/decisoes/0002`).

## Estrutura

```
core/   Kotlin/JVM puro (sem Android): modelo de cena, leitor JSON, composição de shaders, Synth.
        Testes JUnit contra as fixtures da autoria (authoring/test/fixtures).
app/    Android: MainActivity (ecrã inteiro, comando, overlay), SceneRenderer (FBO + passe de saída),
        SceneLibrary (assets), Gl (helpers GLES 3.0), FrameStats (tempos sem alocações).
```

## Como compilar, correr e testar

Requer JDK 17+ (testado com 21) e o Android SDK (platform 36) em `ANDROID_HOME`. A Gradle vem no wrapper.

```
cd tv-app
./gradlew :core:test                 # composição de shaders igual à da autoria (fixtures), JSON, cenas reais
./gradlew :app:assembleDebug         # APK em app/build/outputs/apk/debug/app-debug.apk
adb connect 192.168.1.27:5555
adb -s 192.168.1.27:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.1.27:5555 shell am start -n dev.santavibes.tv/.MainActivity
adb -s 192.168.1.27:5555 logcat -s SantaVibes   # cena, GL, e de 10 em 10 s: fps, intervalo médio/p95/máx, cpu, alloc B/s
```

No comando: **◀ ▶** trocam de cena (mesma ordem da autoria: por estilo, depois id); **OK** mostra/esconde o overlay de diagnóstico. A app também aparece no launcher da TV.

**Zero alocações por frame**: a linha `stats` do logcat inclui `alloc=… B/s` (bytes alocados por todo o processo, contador do ART). Com o overlay escondido deve ficar perto de 0; com o overlay visível há algumas centenas de B/s do texto, na thread de UI.

## A box (medido em 2026-10-04, passo 0.1)

| Item | Valor |
|---|---|
| Modelo | Google TV Streamer (`kirkwood`) |
| Android | 14 (API 34), build `UTTK.260317.003` |
| SoC / GPU | MediaTek MT8696, PowerVR Rogue GE9215, OpenGL ES 3.2 (driver 23.2) |
| RAM | 4 GB |
| Surface | 1920×1080 @ 60 Hz com a TV atual (Samsung 1080p); a box suporta 4K com um ecrã 4K |
| Rede | Ethernet, IP 192.168.1.27 (fixar no router); adb em `192.168.1.27:5555` |
| Extensões relevantes | `GL_EXT_color_buffer_half_float` e `GL_EXT_color_buffer_float` (FBO RGBA16F); **sem** `EXT_disjoint_timer_query` (não há tempos de GPU; mede-se o intervalo entre frames e o tempo de CPU) |

Consequência: `renderScale` é relativo à surface real. Com esta TV, 0.5 significa 960×540. A ferramenta de autoria simula a resolução da surface (1080p por defeito) para que a imagem seja comparável.

## Medições na box

Primeira passagem, 2026-10-05: build debug, perfil `tv`, surface 1920×1080, sinais sintéticos, uma janela de 10 s por cena (logcat `stats`). "Intervalo" é o tempo entre frames; a CPU fica sempre entre 1 e 3 ms, por isso o que limita é a GPU.

| Cena | Render | Limite | fps | Intervalo médio | p95 | Estado |
|---|---|---|---|---|---|---|
| breath | 480×270 | 24 | 23,9 | 41,9 ms | 50,2 ms | ok |
| color-field | 672×378 | 30 | 30,0 | 33,4 ms | 33,6 ms | ok |
| halo | 672×378 | 24 | 24,0 | 41,6 ms | 50,2 ms | ok |
| bars | 960×540 | 30 | 29,8 | 33,6 ms | 33,7 ms | ok |
| geo-tiles | 960×540 | 30 | 30,0 | 33,3 ms | 34,0 ms | ok |
| plasma | 960×540 | 30 | 29,8 | 33,5 ms | 33,8 ms | ok |
| spiral-melt | 960×540 | 30 | 18,8 | 53,2 ms | 55,2 ms | **lenta** |
| glitch-drift | 960×540 | 30 | 16,1 | 62,1 ms | 63,9 ms | **lenta** |
| liquid-light | 960×540 | 30 | 15,2 | 65,9 ms | 66,4 ms | **lenta** |
| marble | 960×540 | 30 | 13,9 | 72,1 ms | 76,2 ms | **lenta** |
| veils | 960×540 | 30 | 13,8 | 72,2 ms | 74,6 ms | **lenta** |
| ink-bloom | 960×540 | 30 | 13,6 | 73,6 ms | 75,3 ms | **lenta** (feedback) |
| flow-particles | 960×540 | 30 | 13,2 | 75,5 ms | 76,3 ms | **lenta** (feedback) |
| aurora | 960×540 | 30 | 12,5 | 80,1 ms | 82,4 ms | **lenta** |
| nebula | 960×540 | 30 | 9,4 | 106,4 ms | 110,1 ms | **lenta** |

**Alocações** (60 s parado na `aurora`, overlay escondido): em 6 janelas, 4 a 0 B/s e 2 com um único bloco TLAB de 32 KB, compatível com a linha de log formatada na thread de UI de 10 em 10 s. O loop de render fica abaixo da resolução do contador (< ~60 B/frame).

### Benchmark (sem vsync), 2026-10-05

`am start -n dev.santavibes.tv/.MainActivity --ez bench true`: o passe de saída sozinho e depois cada cena a 0.25, 0.33, 0.4 e 0.5 (2 s de aquecimento, 4 s de medição). Valores = tempo médio por frame **incluindo** o passe de saída a 1080p.

| | Antes | Ruído por textura (v3) + passe de saída otimizado |
|---|---|---|
| Passe de saída sozinho | 15,7 ms | **9,4 ms** |
| nebula 0.5 / 0.33 | 107,0 / 55,3 ms | 50,5 / **27,4 ms** |
| aurora 0.5 / 0.33 | 80,1 / 43,5 ms | 40,4 / **22,9 ms** |
| marble 0.5 / 0.4 | 71,8 / 51,4 ms | 35,2 / **26,0 ms** |
| liquid-light 0.5 / 0.33 | 65,9 / 37,4 ms | 44,6 / **24,8 ms** |
| plasma 0.5 | 21,1 ms | **15,3 ms** |

Com estes números, cada cena ficou com a `renderScale` que mantém o p95 abaixo de ~28 ms (orçamento de 33,3 ms a 30 fps): 0.33 para aurora, nebula, veils, liquid-light e flow-particles; 0.4 para ink-bloom, marble, glitch-drift e spiral-melt; as restantes sem mudança.

**Por fazer:** confirmar no modo normal (com limite de fps) que todas ficam no limite; teste de 30 min (aquecimento); custo do crossfade quando chegar à tv-app (soma das duas cenas).

