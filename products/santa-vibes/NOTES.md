# Notas — santa-vibes

> Uma página que a próxima sessão lê em vez de repetir uma conversa. Mais recente no topo.
> Factos e decisões, não narrativa. Uma decisão que mude uma regra vai para um ADR.
> O histórico até 2026-10-05 está em `docs/ROADMAP.md` e em `tv-app/README.md`.

## Para a próxima sessão

- 2026-10-09: botão "Abrir controlo" na `phone-app` (pedido de DABS). Falta instalar no Pixel 8 e ver a descoberta
  pelo beacon no telemóvel real: `adb install -r phone-app/app/build/outputs/apk/debug/app-debug.apk`.
- 2026-10-09 (tarde): laboratório de presets, skill `santa-vibes-scene`, cenas `recife` e `skyspace` (19 cenas).
  Galeria atualizada. À espera de DABS: ver as duas cenas na TV. Falta só o projetor (3.5), a última parte.
- 2026-10-09: **3.2–3.4 feitos** (página de controlo, presets do utilizador, estado guardado; decisão 18).
  OK na TV mostra o QR code. À espera de DABS: usar a página no telemóvel (só eu a testei, pelo PC).
  A seguir, fora da Fase 3: laboratório de presets e skill de cenas (decisão 16). O 3.5 espera pelo projetor.
- 2026-10-08 (noite): **galeria de ecrãs** feita e no repo (`authoring/scripts/gallery`, `npm run gallery`;
  https://claude.ai/artifact/4BWzrYabkCxiG5hrytY62L). DABS aceitou a proposta para cenas/presets e Fase 3
  (decisão 16 em `projects/santa-vibes.md`): presets do utilizador na box com exportar. A seguir: 3.1
  (protetor de ecrã), depois a maqueta da página de controlo ao gate antes do 3.2.
- 2026-10-09: **protetor de ecrã retirado** (decisão 17). Fica o `Player` (render fora da `MainActivity`).
- 2026-10-08 (noite): **maqueta do 3.2/3.3 no gate**, à espera de DABS: https://claude.ai/artifact/X7ZmWWsTXniPr4y9R6P5ZN
  (quatro separadores; 4 perguntas com recomendação: o que guarda um preset, exportar, rotação, sem login).
- 2026-10-08: **fases 1 e 2 feitas** (o 1.5 confirmado à tarde, -10 ms; `pc-bridge/README.md`). Provas: https://claude.ai/artifact/5N8oiPNiDede3oQMGhzJ1w.
  Link do Apache feito por DABS (2026-10-08; a box lê `http://192.168.1.200/santa-vibes/`). À espera de DABS: ver na TV as cenas novas e as imagens.
  A `phone-app` foi reinstalada com o tracker novo. A seguir: a Fase 3. Por afinar: o tempo pelo microfone (fraco).
- 2026-10-07: versão reativa das cenas feita (decisão 13), alternável na box com ▲ ou `--es look`.
  DABS escolheu: **a predefinição é a reativa** (`look` por defeito `reactive`); a atual fica a
  um ▲ e mantém o metrónomo sintético (100 BPM sem música). O `default` de `react` nas cenas fica 0:
  a autoria abre na imagem de sempre e a tv-app força o valor.
- 2026-10-07: decisões aplicadas e 1.4 testado num Pixel 8. Falta: a música de referência para afinar
  (tempo pelo microfone fraco; falso 181,5 BPM no primeiro segundo em todas as fontes). Depois: 1.5.
- O `pc-bridge-bg` corre agora sempre no PC de DABS: uma bridge de consola não escuta o beacon
  enquanto ele correr (`--tv` ou `--autostart off`). Ao testar a TV com outra fonte, usar
  `--es source mic|phone` (em `auto` o PC ganha).
- Verificar o estado: `scripts/check santa-vibes` da raiz (inclui agora Rust). Na box: `tv-app/README.md`.

## Dados pessoais

Áudio captado para análise. Nada se grava; à rede local só saem sinais derivados.

| Dado | Finalidade | Retenção | Onde fica |
|---|---|---|---|
| Som que o PC toca (loopback, `pc-bridge`) | Calcular os sinais para a TV | Só em memória, ~6 s de função de onsets e 40 ms de PCM | Processo do `pc-bridge` |
| Som da sala (microfone) ou das apps (playback capture), `phone-app` | Igual | Igual | Processo da `phone-app`, só com o serviço ligado e a notificação visível |
| `deviceId` da TV (número aleatório) | Distinguir TVs no beacon | Enquanto a app estiver instalada | Preferências da tv-app |
| Estado da página de controlo e presets do utilizador (nomes escritos por DABS) | Voltar a abrir a TV como estava; aplicar presets | Até DABS os apagar na página (o estado é reescrito a cada mudança) | `files/control-state.json` e `files/user-presets.json` da tv-app; não saem da box |
| Imagens da pasta de DABS no servidor (`/mnt/drive2/santa-vibes/imagens`) | Mostrar na TV e tirar a paleta | Na cache da box até sair da pasta ou pela LRU (300 MB) | Servidor de casa e `files/images` da tv-app; não saem da rede de casa |

## Encontrado e não resolvido

- 2026-10-06: com a box em standby a app arranca mas fica parada ("App is in stopped state");
  `adb shell input keyevent KEYCODE_WAKEUP` acorda-a (anotado em `docs/REPO.md`).

## Registo

### 2026-10-09 — laboratório de presets, skill de cenas, `recife` e `skyspace`

- Decisão 19: contexto, lâmpadas, IA no PC e interação pelo telemóvel na prateleira.
- Laboratório de presets no `authoring` (botão Laboratório): 9 variações vivas, gravar no `scene.json`
  pelo servidor (a única escrita em `scenes/`). Testado no Chrome sem janela: gravou um diff limpo (revertido).
- Skill `santa-vibes-scene` em `.claude/skills/`: como escrever, verificar e medir uma cena.
- Cenas novas pela skill: `recife` (padrões de Turing; procedural porque o buffer de feedback é também
  o que se vê) e `skyspace` (abertura de céu à Turrell). Números em `tv-app/README.md`. Não vistas por DABS.
- Metaballs e auroras não deram cenas novas: `liquid-light` e `aurora` já o são.

### 2026-10-09 — 3.2 a 3.4: página de controlo

- `ControlServer` (HTTP à mão, sem dependência), `ControlRoutes`, `Controller` (comando e página no mesmo
  estado), `Overlay` com QR code. Core: `Control`, `UserPresets`, `JsonOut`, `QrCode`, com testes.
- Os presets do autor passam a aplicar valores na box (antes só as paletas entravam na rotação).
- QR code: casos de referência gerados uma vez com o pacote npm `qrcode@1.5.4`, só em byte mode
  (`tv-app/core/src/test/fixtures/qr-cases.json`); o pacote não entra no repo. As 32 máscaras forçadas
  batiam à primeira; a escolha automática exigiu ler a penalização como o pacote a lê.
- O curl do Git Bash manda acentos mal codificados: para testar a API com acentos, corpo num ficheiro UTF-8.
- Medições em `tv-app/README.md`. Testado pelo PC e no Chrome emulado a 390 px; não num telemóvel real.

### 2026-10-08 — galeria de ecrãs

- HTML único gerado do repo: embute `scenes/` e `shared/` e usa o `renderer.ts` do authoring. 17 cenas,
  48 presets. Miniaturas: um `Renderer` por cena num só contexto WebGL, copiado para canvas 2D (o browser
  limita os contextos a ~16).
- Visto em Chrome headless (GPU): as 17 compilam e renderizam. Não visto num telemóvel. A `ink-bloom`
  (feedback) começa negra e só ganha forma com o tempo.
- O `UNPACK_FLIP_Y` não se aplica a `ImageBitmap`: as imagens do utilizador passam por um canvas.

### 2026-10-08 — 2.3 cenas híbridas

- `melt-painting` e `light-mosaic` (17 cenas). Vistas no authoring (headless, sem imagem) e na box com imagens
  da NASA e do Met; 30 fps. Primeiro corte deixava zonas negras com imagens do espaço: corrigido.
- A `melt-painting` usa feedback: nada multiplica o frame (acumularia); a música mexe só no fluxo.
- Não vistas por DABS.

### 2026-10-08 — 2.2 e 2.4 acervos: Met e NASA (AIC bloqueada)

- A AIC (primeira proposta) responde às imagens IIIF com um desafio Cloudflare a clientes que não são
  browsers. Não se contorna (seria contornar deteção de bots). O Met passa a primeiro acervo.
- O Met retirou o `/v1/search` a 2026-10-01; usa-se o `/v1.1/search`. O filtro de domínio público da
  pesquisa deixa passar objetos que não o são: confirma-se por objeto.
- NASA Image Library sem chave. Na box: +14 Met e +14 NASA na primeira atualização; 31 em cache (2 MB).
- `--es images` por defeito `all` = server, met, nasa.

### 2026-10-08 — 2.1 imagens reais (ImageProvider)

- Pasta de DABS criada no servidor: `/mnt/drive2/santa-vibes/imagens` (vazia; DABS enche).
  Link do Apache feito por DABS em 2026-10-08 (verificado: 200 e a box lê a listagem):
  `ssh -t dave@192.168.1.200 sudo ln -s /mnt/drive2/santa-vibes/imagens /var/www/html/santa-vibes`.
  Até lá a fonte `server` falha em silêncio e a TV usa a cache e os acervos.
- Testado com um `python3 -m http.server` temporário na pasta (porta 8099, já parado) e 3 imagens
  de teste geradas com ffmpeg; uma imagem de orientação também (apagada). As 3 de teste continuam na
  pasta, para DABS ver a fonte a funcionar; apagar quando quiser.
- Medições em `tv-app/README.md`. Paleta por k-means em OKLab (`PaletteExtract.kt` / `extract.ts`).

### 2026-10-08 — 1.6 sinais reais no authoring

- `pc-bridge` serve os pacotes `SIGN` em `ws://127.0.0.1:47810` (WebSocket escrito à mão, RFC 6455,
  sem dependência; só origens locais). O `pc-bridge-bg` reinstalado já o tem.
- authoring: `sign.ts`, `mixer.ts` (port do `SignalMixer`, igual à TV a < 1e-4 em 240 frames de
  `mixer-cases.json`, gerado pelo `MixerFixtureTest` Kotlin) e a fonte "PC (pc-bridge)".
- Verificado sem a extensão do Chrome (não ligada): sonda Node (61 pkt/s, 0 inválidos, rock 164–171
  BPM) e Chrome headless com `smoke.mjs --pc` (56–59 pkt/s, peso 0,78, BPM lido). Não visto por
  DABS no browser.

### 2026-10-08 — afinação do tempo com música de DABS

- Faixas (decisão 11; DABS deixou-me escolher na pasta `MUSIC` do servidor, só leitura; cópias só no
  scratchpad da sessão): De La Soul "Eye Know", QOTSA "No One Knows", Aphex Twin "Pulsewidth",
  Drexciya "Species Of The Pod", Neil Young "Heart Of Gold", Steve Coleman "Twister", Bad Brains
  "I Against I", Pink Floyd "Breathe" (BBC 1974, ao vivo). Mais os dois loops de teste.
- Referência: estimador offline independente (fluxo espectral, autocorrelação da faixa inteira, pente
  de 4 harmónicos) e tempo local por janelas de 10 s. Confiança baixa no hardcore (~145) e no jazz
  (~169): picos fracos. Certo = a 4 % da referência, da metade ou do dobro.
- Cada faixa com 5 s de silêncio antes (como na cadeia real). Antes → depois:

| Faixa | Ref. BPM | 1.º tempo lido | Tempo errado antes de acertar (s) | % certo | Mudanças de nível |
|---|---|---|---|---|---|
| electro | 139,6 | 181,45 → 139,2 | 1,95 → 0 | 100 → 100 | 12 → 0 |
| eletrónica | 119,4 | 181,45 → 160,3 | 6,6 → 5,9 | 100 → 100 | 0 → 0 |
| folk | 85,4 | 181,45 → 165,3 | 0,5 → 0 | 100 → 100 | 13 → 0 |
| hardcore | 145,4 (?) | 181,45 → 151,8 | 1,5 → 0,25 | 47 → 46 | 10 → 9 |
| hip-hop | 109,1 | 145,9 → 145,9 | 1,5 → 1,2 | 93 → 91 | 0 → 0 |
| jazz | 169 (?) | 181,45 → 163,6 | 0,5 → 0 | 48 → 53 | 14 → 8 |
| rock | 171 | 181,45 → 163,1 | 1,7 → 0,5 | 100 → 100 | 17 → 0 |
| lento ao vivo | 62 | 181,45 → 60,4 | 2,0 → 0 | 63 → 63 | 10 → 0 |
| piano (loop) | 123 | 181,45 → 123,7 | 1,7 → 0 | 56 → 54 | 0 → 0 |
| rock (loop) | 102,5 | 142,95 → 105,2 | 1,7 → 0 | 100 → 100 | 0 → 0 |

- Na cadeia real (PC → TV), hip-hop a partir do silêncio: 108,9–109,4 BPM.
- Custo: fixa ~0,5 s mais tarde; a faixa lenta ao vivo só ao fim de ~12 s.
- Os 4:3 iniciais (eletrónica, hip-hop) são introduções ambíguas; não os forcei.
- Scripts (`refbpm.py`, `tempo_eval.py`) ficaram no scratchpad; o método está aqui.

### 2026-10-07 — 1.5: sem MIDI; latência medida na sala

- DABS tirou o MIDI clock do produto (ADR 0003). O `sourceKind` 4 e o bit 2 das flags ficam
  reservados; o layout e a versão do pacote não mudam. A fixture foi regenerada sem o caso MIDI.
- **Método** (`scripts/latency.py`):
  - O Pixel 8 está virado para a TV. A câmara abre por adb mesmo bloqueado
    (`STILL_IMAGE_CAMERA_SECURE`), com toque em Vídeo e no obturador.
  - O PC toca cliques a 115 BPM e a TV mostra o `halo` na versão reativa.
  - A hora dos cliques sai da correlação do áudio do vídeo com a faixa, porque pelo microfone da
    sala os cliques são fracos e a deteção por limiar falhou.
  - O pico de brilho da TV sai da primeira harmónica, por fase do beat.
- **Resultado com 60 ms:** a imagem chegou 71 ± 6 ms **antes** do som (15 blocos de 8 beats; a TV
  fixou 115,0 BPM). O `pc-bridge` passa a -10 ms por defeito. O `signals-core` aceita agora um
  lookahead negativo: o `fract` passou a `rem_euclid`, com teste.
- **Hipótese, por confirmar:** o loopback apanha o áudio antes do buffer de saída e das colunas.
  Se a segunda medição não der perto de 0, as causas a ver, da mais barata para a mais cara:
  - viés de fase do tempo com cliques vs. música;
  - a parte `drive` (graves, sem previsão) a puxar o pico;
  - a câmara a ajustar a exposição.
- **Não medido:** a fonte do telemóvel (continua com 60 ms). O valor vale para esta sala e estas
  colunas; o projetor vai pedir uma medição própria.
- A segunda medição não se fez: a TV entrou em standby a meio, às ~23h, e não a voltei a ligar.
- `pc-bridge --autostart on` chamado de um script fica à espera, porque o processo de fundo herda
  os handles do pai. Está anotado no README e não foi corrigido.

### 2026-10-07 — versão reativa das cenas (decisão 13)

- Parâmetro `react` em todas as cenas (0 = imagem de sempre, 1 = reativa), forçado pela tv-app com
  `--es look current|reactive` ou ▲. Sem mudar o formato; convenção em `docs/FORMATO-CENA.md`.
- Primeira tentativa (pulse ×3 sobre `uBeat.x`, graves em tamanho e forma) mediu só 1,9 % na nebula:
  com música as bandas ficam quase no topo do AGC (graves 0,86–0,99 com o piano), por isso mexem pouco,
  e o envelope do beat sobe num frame e cai em 0,18 s. Ficou o `groove()` (`lib/react.glsl`): cosseno
  elevado ao quadrado sobre `uBeat.y`, pico em cada beat, sem saltos. `beat = mix(pulse·uBeat.x,
  4·pulse·groove, react)`; `drive = react·(0,4·graves + 0,6·groove)` nos termos de forma.
- Sem música a reativa ampliava o metrónomo sintético de 100 BPM (dobra de 2,9 % sem música); na reativa
  o `Synth` fica sem beat e sem punch. Na atual não muda nada.
- Medido na box (gravação 50 s, música 10–43 s pelo `pc-bridge-bg`; brilho médio fora do overlay; scripts
  no scratchpad da sessão, método igual ao da página de estado):

| Cena · música | Pulso no tempo, atual → reativa (% do brilho) | Pico a pico por beat | Movimento (dif. entre frames), atual → reativa |
|---|---|---|---|
| nebula · piano 123 | 0,33 → 5,29 | 0,7 → 10,2 % | 0,13 → 1,21 |
| nebula · rock ~103 | 0,62 → 8,15 | 1,3 → 15,6 % | 0,14 → 1,25 |
| halo · piano | 0,10 → 5,25 | 0,3 → 11,3 % | 0,07 → 1,96 |
| liquid-light · piano | 0,54 → 4,71 | 1,3 → 9,8 % | 0,42 → 2,24 |
| color-field · piano | 0,23 → 5,38 | 0,5 → 10,9 % | 0,10 → 1,08 |

- Sem piscar: a parte da variação acima de 4 Hz desce na reativa (0,07–0,15 contra 0,10–0,22). Sem música
  a reativa fica parada no ritmo (dobra ≤ 0,1 %). Custo: benchmark das 15 cenas a escala 0,5 igual ao ms
  nas duas versões; 30/24 fps no limite em todas as gravações.
- Não verificado: as outras 11 cenas só se viram compilar e no benchmark, não a olho; a autoria no
  browser não foi aberta (o slider `react` aparece pelo `scene.json`).

### 2026-10-07 — 1.4 num telemóvel real

- Pixel 8, Android 17, ligado por USB; DABS ligou-o ao Wi-Fi de casa (estava nos dados móveis).
- Descoberta pelo beacon em 1,3 s. Microfone até à box: o som chega (energia 0,92 com o piano),
  mas o tempo pela sala é fraco. Som do telemóvel (Files by Google): 123,8 BPM no piano de 123.
- Para medir só o telemóvel é preciso parar o `pc-bridge-bg` (também responde ao beacon do
  `fake_tv`); neste PC o beacon do `fake_tv` às vezes sai pelo adaptador do WSL.
- Ficheiro de teste copiado para o telemóvel e apagado no fim; `pc-bridge-bg` voltou a correr.

### 2026-10-07 — decisões de DABS aplicadas

- Formato de cena 4 (`uDeltaTime`, por slot nos dois hosts); `flow-particles` por segundo; `image:
  optional` definido. Na box: 15 cenas compilam, `flow-particles` a 30 fps, 0 B/s.
- Predefinições da box: `fadeEvery 2`, paleta a rodar, reatividade 0,7.
- `scripts/check` com `cargo test` + clippy do `signals-core` e do crate nativo; apanhou logo um
  aviso no teste do fixture do 1.3.
- `pc-bridge`: biblioteca + `pc-bridge`/`pc-bridge-bg`; `--autostart on` ligado no PC de DABS.
- O telemóvel `3B241FDJH000QX` já não estava ligado: o teste do 1.4 fica para quando estiver.

### 2026-10-06 — Servidor de casa (levantamento) e escolha do projetor

- Servidor `obelisk-server`, `192.168.1.200` (SSH `dave`): Ubuntu 24.04, AMD FX-6300, 7,7 GB RAM,
  GeForce GT 710 (Kepler, sem driver), SSD 112 GB + HDD 1 TB (`/mnt/drive2`, 343 GB livres).
  Corre Samba, MiniDLNA e Apache (página por defeito). Ethernet negociada a 100 Mb/s.
- Serve para: fonte de imagens por HTTP e preparação noturna (descarga dos acervos,
  redimensionar) na Fase 2. Sempre opcional: a box continua offline-first com a cache.
- Não serve para: imagens por IA (GPU sem CUDA/PyTorch atual, CPU sem AVX2), renderizar para o
  projetor, sinais de áudio.
- Projetor escolhido, ainda não comprado: **Magcubic L018** (106,99 € na PcComponentes, vendido
  pela loja, devolução grátis). Critérios de DABS: apontar para qualquer lado (teto incluído),
  silencioso, Android para a `tv-app`, ligado à corrente (bateria dispensável); uso só em salas
  escuras (noite, estúdio no sótão), por isso o brilho pouco importa. Riscos: ruído da ventoinha
  (medição de 54 dBA colado / ~32 dBA à distância num modelo irmão; modo Eco baixa) e GPU
  Mali-G31 com 1 GB RAM. Plano B: Wanbo Vali 1 Pro (273,44 € na PowerPlanet, Google TV 14,
  suporte de 200°, ≤ 28 dB). Comparação e preços: doc "Projetor de mood — comparação"
  (https://claude.ai/code/artifact/a8534f05-4afb-426d-a135-5ee5e6b8b706), atualizado pela skill
  local `/precos-projetor` de DABS.

### 2026-10-06 — 1.4 `phone-app`

- Instalados no PC: NDK 30.0.16248370, emulador e imagem Android 16 x86_64 (AVD `sv-phone`),
  targets Android do Rust, `cargo-ndk` 4.1.2. O `sdkmanager.bat` parte nomes com `;` — usar
  `--package_file`.
- App sem dependências; JNI sem crate (tabela de funções). Gradle chama o `cargo ndk` com o
  caminho absoluto do `cargo` (o daemon não tem `~/.cargo/bin` no PATH).
- Android 15+ desenha por baixo das barras do sistema: o ecrã usa os insets.
- Testado no emulador até à box (`src=mic`). Não testado: microfone real, música de uma app
  real, beacon por Wi-Fi. Há um telemóvel ligado por USB ao PC (`3B241FDJH000QX`) em que não
  mexi: instalar e tocar no ecrã remotamente precisa do OK de DABS.

### 2026-10-06 — 1.3 receção na tv-app

- `core`: `SignPacket` (decode sem alocação, verificado contra o fixture gerado pelo Rust:
  `signals-core/tests/fixtures/sign-cases.json`), `SignalMixer`, `SequenceGate`; 8 testes novos.
- `app`: `SignalHub` (recvfrom sem endereço, beacon), mixer ligado aos uniforms, overlay e logcat.
  Faltava a permissão `INTERNET` no manifest.
- Medido na box com o `pc-bridge`: descoberta pelo beacon, 60 pkt/s, 0 B/s, volta ao Synth.
  As alocações que apareceram (55 KB/s) eram do overlay visível, não da receção.
- `SceneRenderer.kt` tinha passado as 300 linhas desde o 0.6: passe de saída em `OutputPass.kt`.
- Reatividade por defeito 1 (pergunta 4 alargada).

### 2026-10-06 — 1.2 `pc-bridge`

- Binário Rust com `cpal` (loopback WASAPI) + `signals-core` + UDP; `fake_tv` como TV de teste.
- Erro encontrado no primeiro teste: o loopback reporta Xrun em cada início/fim de reprodução e
  eu reabria a captura por isso (só saíam 2 pacotes em 2 s). Agora só reabre quando o dispositivo
  some ou a saída por defeito muda.
- Medido com dois loops reais pelas colunas: 60 pkt/s, 0 perdidos, 1,6 % de um núcleo, 11,9 MB.
  Pacotes chegam à box (`nc` no adb). Números no `pc-bridge/README.md`.
- Não feito: arranque automático, ícone, correr sem consola.

### 2026-10-06 — 1.1 `signals-core`

- Rust instalado no PC (`rustup` via winget, stable 1.99, MSVC).
- Crate `signals-core`: espectro, bandas com AGC, centroid, onsets, tempo, fase com PLL e
  lookahead, mood, pacotes `SIGN`/`PNMB`. Algoritmo e constantes em `signals-core/README.md`.
- Três correções vindas dos testes: (1) AGC por banda isolado fazia leakage de −72 dB ler 0,39 →
  o piso de cada banda segue o nível geral (−20 dB); (2) 150 BPM lido como 75 porque o período
  fracionário (37,5 frames) falhava os picos estreitos → função de onsets suavizada antes da
  autocorrelação; (3) ruído dava onsets a mais → limiar de 1,5 para 3 desvios, confiança mínima 0,3.
- Loops reais de freesound (só leitura, fora do repo): 123 BPM certo no único com tempo conhecido;
  nos de bateria não há verdade para comparar. Possível erro de oitava fica para afinar.
- Não entrou no `scripts/check`/CI (pergunta 2).

### 2026-10-06 — 0.6 na tv-app, e arrumação

- Antes: check verde, CI verde, smoke em GPU das 15 cenas no authoring OK. Sem erros das fases
  anteriores além das duas pontas soltas acima.
- `core`: `Drift.kt` e `PaletteVariation.kt`, portes do authoring. O drift cumpre as fixtures
  partilhadas a 1e-9; a paleta ganhou fixture própria (`authoring/test/fixtures/palette-cases.json`,
  gerada por `authoring/scripts/palette-cases.mjs`) verificada pelos dois lados a 1e-6.
- `app`: `SceneSlot` (uma cena: programa, FBOs à sua escala, relógio, valores), crossfade de 4 s
  como no authoring, `Benchmark` em ficheiro próprio. Definições por extras do intent.
- Compilação de todas as cenas no arranque (+0,9–1,1 s): tira o engasgo de 50–95 ms no início
  de cada crossfade.
- Medido na box: 6 de 15 transições caem para 24–26 fps com a cena que sai a mexer; 13/15 no
  limite a meio ritmo; 15/15 com ela parada. Tabela em `tv-app/README.md`. Ficou o comportamento
  documentado (`fadeEvery 1`) até DABS decidir.
- Sem alocações por frame com drift, rotação e matiz ligados (mesmo padrão que com tudo desligado).
- Arrumação: `CLAUDE.md` de 86 para 35 linhas; mapa, comandos, convenções e ambiente passaram
  para `docs/REPO.md` (decisão 2 do projeto). Este ficheiro criado.
