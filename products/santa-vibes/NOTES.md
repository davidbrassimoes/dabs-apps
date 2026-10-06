# Notas — santa-vibes

> Uma página que a próxima sessão lê em vez de repetir uma conversa. Mais recente no topo.
> Factos e decisões, não narrativa. Uma decisão que mude uma regra vai para um ADR.
> O histórico até 2026-10-05 está em `docs/ROADMAP.md` e em `tv-app/README.md`.

## Para a próxima sessão

- Fase 0 feita; 1.1 e 1.2 feitos em 2026-10-06. Próximo: 1.3 (receção na tv-app: `SignalHub`,
  beacon, mixer de reatividade). Com ele a box reage à música do PC e afina-se tudo a olho.
- À espera de DABS (desde 2026-10-06): as perguntas 2 a 7 em `../../projects/santa-vibes.md`
  ("Por decidir"). Nenhuma bloqueia o 1.2.
- Verificar o estado: `scripts/check santa-vibes` da raiz. Na box: `tv-app/README.md`.

## Dados pessoais

Nenhum por agora. A Fase 1 vai captar áudio (microfone ou playback no telemóvel, loopback no
PC); só se processam sinais derivados (bandas, beat), nada se grava nem sai da rede local.
Rever esta tabela quando o 1.4 entrar.

| Dado | Finalidade | Retenção | Onde fica |
|---|---|---|---|

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
