# Notas — santa-vibes

> Uma página que a próxima sessão lê em vez de repetir uma conversa. Mais recente no topo.
> Factos e decisões, não narrativa. Uma decisão que mude uma regra vai para um ADR.
> O histórico até 2026-10-05 está em `docs/ROADMAP.md` e em `tv-app/README.md`.

## Para a próxima sessão

- Fase 0 feita (0.6 fechado em 2026-10-06). Próximo: Fase 1, passo 1.1 (`signals-core` em Rust).
- À espera de DABS (desde 2026-10-06): o ritmo da cena que sai durante o crossfade, e mais três
  perguntas em `../../projects/santa-vibes.md` ("Por decidir").
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
