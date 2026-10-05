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

| Cena | renderScale | fps alvo | frame time médio | frame time p95 | Notas |
|---|---|---|---|---|---|
| _a preencher no passo 0.5_ | | | | | |

