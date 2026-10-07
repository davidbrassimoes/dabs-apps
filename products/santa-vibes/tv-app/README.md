# tv-app — app Android TV (Fase 0.4–0.6 e Fase 3)

Kotlin + OpenGL ES 3.0 nativo. Renderer de cenas, receção de sinais, fontes de imagem, servidor de controlo e `DreamService`.

Regras: ver `CLAUDE.md` (zero alocações por frame, sem WebView, dependências justificadas).

- `scenes/` e `shared/` são copiados para assets **gerados** em `app/build/` pela tarefa `copySceneAssets` em cada build. Nunca há cópias em `src/`.
- Sem dependências de runtime (ADR `docs/decisoes/0002`).

## Estrutura

```
core/   Kotlin/JVM puro (sem Android): modelo de cena, leitor JSON, composição de shaders, Synth,
        drift de parâmetros (Drift), variação de paleta (PaletteVariation), pacotes de sinais
        (SignPacket) e mixer de reatividade (SignalMixer, SequenceGate).
        Testes JUnit contra as fixtures da autoria (authoring/test/fixtures).
app/    Android: MainActivity (ecrã inteiro, comando, overlay), SceneRenderer (cenas, crossfade, passe de saída),
        SceneSlot (uma cena: programa, FBOs, relógio), OutputPass, SignalHub (receção UDP e beacon),
        Benchmark (passo 0.5), SceneLibrary (assets),
        Gl (helpers GLES 3.0), FrameStats (tempos sem alocações).
```

## Como compilar, correr e testar

Requer JDK 17+ (testado com 21) e o Android SDK (platform 36) em `ANDROID_HOME`. A Gradle vem no wrapper.

```
cd tv-app
./gradlew :core:test                 # composição, drift e paleta iguais aos da autoria (fixtures), JSON, cenas reais
./gradlew :app:assembleDebug         # APK em app/build/outputs/apk/debug/app-debug.apk
adb connect 192.168.1.27:5555
adb -s 192.168.1.27:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.1.27:5555 shell am start -n dev.santavibes.tv/.MainActivity [--es scene nebula]
adb -s 192.168.1.27:5555 logcat -s SantaVibes   # cena, GL, e de 10 em 10 s: fps, intervalo médio/p95/máx, cpu, alloc B/s
```

No comando: **◀ ▶** trocam de cena (mesma ordem da autoria: por estilo, depois id); **OK** mostra/esconde o overlay de diagnóstico. A app também aparece no launcher da TV.

**Zero alocações por frame**: a linha `stats` do logcat inclui `alloc=… B/s` (bytes alocados por todo o processo, contador do ART). Com o overlay escondido deve ficar em 0 (com um bloco TLAB de 3–32 KB de vez em quando, do log da UI); com o overlay visível sobe para ~55 KB/s (o texto refeito 2× por segundo, na thread de UI), por isso mede-se sempre com ele escondido.

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

### Modo normal com as escalas novas, 2026-10-05

Uma janela de 10 s por cena, com o limite de fps de cada cena: **todas no limite** (30,0 fps; breath e halo a 24), intervalo médio 33,3 ms e **p95 33,5 ms** em todas. O máximo de 45–57 ms só aparece nas janelas com troca de cena (compilação do shader); nas janelas sem troca, máximo ~34 ms e `alloc=0 B/s`.

### Teste de 31 min na `nebula` (a cena mais apertada), 2026-10-05

`am start ... --es scene nebula`, 634×356, limite 30. **187 janelas de 10 s, todas a 30,0 fps**, p95 entre 33,45 e 33,53 ms, pior frame 42 ms, `alloc=0 B/s` exceto um bloco TLAB de 32 KB a cada ~30–40 s (o log da thread de UI). Sem throttling: estado térmico 0 durante todo o teste; SoC a 51,8 °C no fim (leitura "Current temperatures from HAL" do `dumpsys thermalservice`; a secção "Cached" não é atualizada e não serve para curvas).

**Conclusão:** com o ruído por textura, o passe de saída otimizado e a escala por cena, as 15 cenas cumprem o critério da Fase 0 na box. A escala dinâmica não é precisa por agora; volta a ser avaliada com um projetor 4K ou cenas novas mais pesadas.

### Drift, paleta e crossfade (passo 0.6), 2026-10-06

Definições do host por extras do intent até haver página de controlo (Fase 3); por defeito (decisão de DABS, 2026-10-07): crossfade 4 s com a cena que sai a meio ritmo, drift 1, paleta a rodar (180 s cada, transição de 20 s), sem matiz, reatividade 0,7. Até 2026-10-06 eram iguais ao authoring (paleta parada, reatividade 1, `fadeEvery 1`).

```
am start -n dev.santavibes.tv/.MainActivity [--ef transition 4] [--ef drift 1] [--ef driftSpeed 1]
    [--ez rotate true] [--ef hold 180] [--ef fade 20] [--ef hue 30] [--ei fadeEvery 1]
```

No fim de cada crossfade o logcat mostra `transition <de> -> <para>: frames=… interval avg=… max=… cpu max=…`. O overlay mostra drift, paleta atual e progresso da transição.

**Compilação no arranque.** Trocar de cena compilava o shader no thread de GL: 50–95 ms parados no início de cada crossfade. Agora as 15 cenas compilam (e desenham uma vez num alvo 1×1, para drivers que só acabam a compilação no primeiro draw) ao criar o contexto: **+0,9–1,1 s no arranque**, e o pior frame de uma transição passou de 60–95 ms para ~42 ms.

**Custo do crossfade.** Durante a mistura renderizam-se as duas cenas. Volta pelas 15 cenas (D-pad, crossfade de 4 s), por `fadeEvery` (de quantos em quantos frames a cena que sai é renderizada):

| `fadeEvery` | Transições no limite de fps | As outras |
|---|---|---|
| 1 (a cena que sai continua a mexer; é o que diz `ARQUITETURA.md`) | 9 de 15 | 24–26 fps (38,3–45,7 ms) nas que juntam duas cenas pesadas: nebula → veils, glitch-drift → liquid-light, liquid-light → marble, marble → spiral-melt, spiral-melt → flow-particles, ink-bloom → nebula |
| 2 (a cena que sai a meio ritmo) | 13 de 15 | liquid-light → marble 35,0 ms, ink-bloom → nebula 37,4 ms |
| 0 (a cena que sai fica parada no último frame) | 15 de 15 | — |

Transições de/para breath e halo ficam a 41,5 ms porque essas cenas têm limite de 24 fps (é o esperado). Por defeito `fadeEvery 2` desde 2026-10-07 (decisão de DABS; `ARQUITETURA.md` atualizada).

**Alocações:** com drift, rotação de paleta e matiz ligados, o padrão de `alloc` é igual ao de drift desligado (só o bloco TLAB periódico do log da UI); o código novo não aloca por frame.


### Receção de sinais (passo 1.3), 2026-10-06

A app escuta pacotes `SIGN` em UDP 47800 e envia o beacon `PNMB` de 2 em 2 s para 255.255.255.255:47801
(`deviceId` aleatório guardado nas preferências; `controlPort` 0 até haver ControlServer). Permissão `INTERNET`.

```
am start -n dev.santavibes.tv/.MainActivity [--ef reactivity 1] [--es source auto|pc|phone|mic|none]
```

- **Receção** (`SignalHub`, thread própria): `Os.recvfrom` para um buffer fixo, sem pedir o endereço de origem, por isso não aloca por pacote. Guarda o último pacote por tipo de fonte; descarta pacotes fora de ordem, mas aceita um emissor que reinicia (2 s sem pacotes ou salto para trás > 1000).
- **Escolha da fonte**: `auto` usa a primeira com som por esta ordem: PC, telemóvel (playback), microfone; senão a primeira que está a enviar.
- **Mixer** (`SignalMixer`, thread de GL): `saída = mix(Synth, real, reatividade × presença)`. Valores suavizados em 30 ms; a fase do beat anda sozinha ao bpm recebido entre pacotes e é puxada para a de cada pacote; fase, bpm e onset vêm da fonte que pesa mais (não se misturam). Presença sobe em 0,5 s; cai em 1,5 s depois de 1,5 s sem pacotes ou 4 s só com silêncio. Reatividade 0 = exatamente o Synth (teste).
- **Logcat/overlay**: `signals: source pc|none` quando muda; a linha `stats` acrescenta `src`, `w` (peso), `bpm`, `pkt/s`.

Medido com o `pc-bridge` no PC e dois loops reais pelas colunas, cena `bars`:

| | Resultado |
|---|---|
| Descoberta | o `pc-bridge` encontrou a box pelo beacon, sem `--tv` |
| Receção | 59–62 pkt/s, `src=pc`, peso 1,00, 123,9 BPM (piano 123) e 102,5 (rock); 30,0 fps, p95 33,5 ms |
| Alocações | **0 B/s** com 60 pkt/s e overlay escondido |
| PC desligado a meio | fonte `none` 2,1 s depois (1,5 s + tick de 0,5 s do log); peso a 0 até 3 s (teste do mixer) |
| Reatividade 0 | 60 pkt/s recebidos, peso 0,00 |

Não verificado: se a reação é agradável à vista (isso é a afinação, com DABS a ver).

### Versão reativa das cenas (para comparar), 2026-10-07

Decisão 13 de DABS (`projects/santa-vibes.md`): cada cena tem o parâmetro `react` (0 = a imagem atual,
1 = reativa) e a app força-o em todas as cenas, sem reinstalar.

```
am start -n dev.santavibes.tv/.MainActivity [--es look current|reactive]   # defeito: reactive (DABS, 2026-10-07)
```

- **Comando**: ▲ alterna atual ↔ reativa ao vivo. O overlay mostra `versão: atual|reativa`; o logcat escreve
  `look=` quando muda e na linha `stats`.
- Na reativa o `Synth` não toca metrónomo (beat e punch a 0): sem música a cena fica calma em vez de pulsar
  a 100 BPM sintéticos.
- O que muda em cada cena: comentário `Reactive variant` no `scene.frag`; o `groove()` está em
  `shared/glsl/lib/react.glsl`. Medições em `NOTES.md`.
