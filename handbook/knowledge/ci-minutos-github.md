# Minutos do GitHub Actions em repos privados

**Atualizado:** 2026-10-29, a partir dos repos dabs-apps, webdabs, digital-obelisk e ps2-ports.

## O que sabemos

- **documentado:** num repo privado, os runners do GitHub gastam a quota mensal do plano
  (no Free, 2000 minutos por conta ou organização). Cada job é cobrado à parte e
  arredondado ao minuto. Windows conta a dobrar, macOS a dez vezes. Repos públicos não
  gastam quota.
- **documentado:** um job saltado por `if` ao nível do job não arranca runner e não paga.
  Um job cujos passos são todos saltados por `if` paga na mesma: o runner arrancou.
- **medido (outubro 2026):** os jobs por produto do dabs-apps saltavam os passos e não o
  job. Cada corrida arrancava os cinco jobs, um deles em Windows. São pelo menos 6 minutos
  por corrida mesmo quando só mudava um `.md`, em 105 corridas. A escarpa-audio esgotou a
  quota ao sexto dia do primeiro mês (ADR-021 desse repo) e passou para um runner próprio.
- **medido (outubro 2026):** no webdabs, 38 dos 42 commits em main foram diretos, quase
  todos de documentação, e cada um corria os 7 jobs pesados (Postgres, Playwright,
  Lighthouse, imagens Docker).
- **medido (2026-10-29):** num valor YAML sem aspas, ` #` abre um comentário, também
  dentro de `${{ }}`. Uma condição com `'Merge pull request #'` ficou cortada e o GitHub
  rejeitou o ficheiro ("workflow file issue", corrida sem jobs). Um parser YAML local não
  avisa. O mesmo erro de YAML, com `: ` num `name:`, tinha o CI do ps2-ports parado desde o
  slice 0.
- **medido (2026-10-29):** o `scripts/changed.sh` nunca filtrou nada na CI do dabs-apps e
  do ps2-ports. Numa PR fazia `git fetch` da base, que falha sem credenciais
  (`persist-credentials: false`); num push lia `GITHUB_EVENT_BEFORE`, que o GitHub não
  define. Em ambos dizia "Sem commit de base: corre tudo" e cada corrida construía todos os
  produtos. Agora lê a base do payload do evento (`$GITHUB_EVENT_PATH`, com `jq`) e, quando
  não a encontra, deixa um `::warning::` visível na corrida.

## O que decidimos e porquê

Padrão do `ci.yml` do dabs-apps e do ps2-ports, desde 2026-10-29 (nasceu no
`workspace-template`, apagado a 2026-10-29; esta nota mudou-se para aqui):

1. O primeiro job (`handbook`) corre `scripts/changed.sh` para cada produto e expõe o
   resultado em `outputs`. O job de cada produto tem `needs: handbook` e
   `if: !cancelled() && needs.handbook.outputs.<produto> == 'true'`.
2. O push para main de um merge de PR não corre: a PR já correu. Os commits diretos em
   main continuam a correr. O merge reconhece-se por o commit ser do GitHub
   (`github.event.head_commit.committer.email == 'noreply@github.com'`), o que serve para
   merge commit e para squash. Até 2026-10-29 dabs-apps, ps2-ports e webdabs usavam a
   mensagem (`Merge pull request ...`), que falhava com squash.
3. Com `workflow_dispatch`, `scripts/changed.sh` não tem commit de base e corre tudo.
4. Um repo sem `changed.sh` por produto (o webdabs) filtra no `on:` com `paths`: ignora
   `**/*.md` e volta a incluir à mão os `.md` que algum teste lê.

## Perguntas abertas

- Um merge testado na PR contra um main antigo não volta a ser testado depois do merge.
  Se isto partir main, o mais barato é correr `gh workflow run ci.yml` à mão. O passo
  seguinte é voltar a correr os merges só quando main avançou desde a última corrida da PR.
- Um commit feito no editor web do GitHub também fica com o GitHub como autor e salta a
  CI de push. Se isso esconder uma avaria, correr `gh workflow run ci.yml` à mão.
- Runner próprio (o obelisk-server para Linux, o portátil para Windows) ainda não decidido.

## Fontes

- docs.github.com, "About billing for GitHub Actions" e "Using conditions to control job
  execution".
- `gh run list` dos quatro repos, 2026-10-01 a 2026-10-29.
