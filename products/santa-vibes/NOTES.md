# Notas — santa-vibes

> Uma página que a próxima sessão lê em vez de repetir uma conversa. Mais recente no topo.
> Factos e decisões, não narrativa. Uma decisão que mude uma regra vai para um ADR.
> O histórico até 2026-10-05 está em `docs/ROADMAP.md` e em `tv-app/README.md`.

## Para a próxima sessão

- Fase 0 feita; 1.1–1.3 feitos e 1.4 quase (falta telemóvel real) em 2026-10-06. A box reage à
  música do PC. Próximo: testar a `phone-app` num telemóvel com DABS, afinar a olho (pergunta 7), 1.5.
- À espera de DABS (desde 2026-10-06): as perguntas 2 a 7 em `../../projects/santa-vibes.md`
  ("Por decidir"). Nenhuma bloqueia o 1.2.
- Verificar o estado: `scripts/check santa-vibes` da raiz. Na box: `tv-app/README.md`.
- Quando o projetor (Magcubic L018) chegar, dentro do prazo de devolução: instalar a `tv-app` por
  `adb`, correr o benchmark nas 15 cenas, afinar `shared/profiles/projector.json` (passo 3.5) e
  DABS ouve a ventoinha em modo Eco no sótão. Se não convencer, devolver e passar ao Vali 1 Pro.
  Detalhes no registo de 2026-10-06 abaixo.

## Dados pessoais

Áudio captado para análise. Nada se grava; à rede local só saem sinais derivados.

| Dado | Finalidade | Retenção | Onde fica |
|---|---|---|---|
| Som que o PC toca (loopback, `pc-bridge`) | Calcular os sinais para a TV | Só em memória, ~6 s de função de onsets e 40 ms de PCM | Processo do `pc-bridge` |
| Som da sala (microfone) ou das apps (playback capture), `phone-app` | Igual | Igual | Processo da `phone-app`, só com o serviço ligado e a notificação visível |
| `deviceId` da TV (número aleatório) | Distinguir TVs no beacon | Enquanto a app estiver instalada | Preferências da tv-app |

## Encontrado e não resolvido

- 2026-10-05, do 0.3: `flow-particles` avança e desvanece por frame (não por segundo); com o
  limite de fps fixo não se nota. Durante um crossfade a 24 fps (ver abaixo) anda mais devagar.
  Correção limpa: `uDeltaTime` no contrato (muda o formato de cena; pedir antes).
- 2026-10-05, do 0.3: `color-field` declara `image: optional` mas não lê `uImage`. Se `optional`
  quer dizer "a paleta pode vir da imagem", está certo; se quer dizer "amostra a imagem", passa a
  `none`. O formato de cena não distingue. Decidir quando a Fase 2 chegar.
- 2026-10-06: com a box em standby a app arranca mas fica parada ("App is in stopped state");
  `adb shell input keyevent KEYCODE_WAKEUP` acorda-a (anotado em `docs/REPO.md`).

## Registo

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
