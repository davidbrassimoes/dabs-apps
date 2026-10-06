# Notas — sotao

> Uma página que a próxima sessão lê em vez de repetir uma conversa. Mais recente no topo.
> Factos e decisões, não narrativa. Uma decisão que mude uma regra vai para um ADR.

## Para a próxima sessão

- **Publicada (gate 3, DABS, 2026-10-06):** https://sotao.escarpa-audio.workers.dev, Worker `sotao`,
  versão `e9f6e8e2`, D1 `sotao` (WEUR). 118 testes. Aspeto G; D1 em vez de Turso (decisão 8).
- **Verificado em produção:** o primeiro pedido migrou e semeou (1,3 s; 47 peças, 32 cartas, 1
  migração); pedidos seguintes ~0,1 s; `foreign_keys = 1`; apagar `ac30` (usado por cartas)
  falha com `FOREIGN KEY constraint failed` e nada muda.
- **Por verificar em produção:** telemóvel real, atalhos de teclado, offline. Login, importação
  com commit no D1 remoto (188 cartas num só `batch`) e edição/arquivo de cartas verificados a
  2026-10-06 (ver Registo).
- Verificar: `scripts/check` (desta pasta) ou `scripts/check sotao` (da raiz).

## Dados pessoais

| Dado                            | Finalidade                           | Retenção               | Onde fica                                  |
| ------------------------------- | ------------------------------------ | ---------------------- | ------------------------------------------ |
| Password única (`APP_PASSWORD`) | Proteger a app de um só utilizador   | Enquanto a app existir | `.env` local; secret do Worker em produção |
| Cookie de sessão assinado       | Manter a sessão aberta (dura um ano) | Um ano ou até sair     | Browser do utilizador                      |

Cartas, material e histórico de tiragens não são dados pessoais de terceiros.

## Paleta

Medida a 2026-10-06 com a fórmula WCAG 2.x (luminância relativa), sobre cada superfície onde
o token aparece. Texto >= 4,5:1, gráficos >= 3:1. Valores em `src/routes/layout.css`.

| Par (texto sobre superfície)                                | Escuro | Claro |
| ----------------------------------------------------------- | ------ | ----- |
| `--text` sobre `--ground`                                   | 15,45  | 14,60 |
| `--text` sobre `--panel`                                    | 13,46  | 12,59 |
| `--text-dim` sobre `--ground`                               | 7,89   | 6,41  |
| `--text-dim` sobre `--panel` (pior de `--text-dim`)         | 6,88   | 5,53  |
| `--ground` sobre `--text` (comutador ativo)                 | 15,45  | 14,60 |
| `--lamp-ink` sobre `--lamp` (botão Draw)                    | 8,32   | 6,10  |
| `--lamp` sobre `--ground` (foco, gráfico)                   | 8,36   | 5,05  |
| `--lamp` sobre `--panel` (foco, gráfico)                    | 7,29   | 4,35  |
| `--kind-source` sobre `--panel`                             | 6,99   | 4,59  |
| `--kind-treatment` sobre `--panel`                          | 7,91   | 4,62  |
| `--kind-technique` sobre `--panel`                          | 7,69   | 5,00  |
| `--kind-constraint` sobre `--panel` (pior escuro)           | 5,81   | 4,62  |
| `--kind-arrangement` sobre `--panel`                        | 7,08   | 5,01  |
| Tinta da carta sobre o fundo do tipo (pior: constraint)     | 6,36   | 6,36  |
| Tinta da carta a 80 % (linha de material, pior: constraint) | 4,58   | 4,58  |
| Tinta da carta ideia (`#1C1814` / `#F1EADD` invertida)      | 13,33  | 14,75 |

- **Ajustados em relação a `LOOK.md`** (só luminosidade, falhavam sobre `--panel` no claro, onde as
  linhas da lista de cartas e os chips de receita os usam): `--kind-source` `#3E6B5D` -> `#3A6759`
  (4,32 -> 4,59) e `--kind-treatment` `#855A12` -> `#7F5611` (4,32 -> 4,62).
- Sem alteração: `--text-dim` sobre `--line` no claro dava 4,28; em vez de mexer na cor, o
  hover das linhas de cartas passou a `--ground` (6,41).
- **Não cumpre 3:1** (e `LOOK.md` manda assim): o fundo das cartas sobre `--ground` no tema claro
  (1,69 a 2,30; a ideia, invertida, dá 14,60). A carta é um bloco de leitura, não um controlo,
  e o texto dentro dela passa. Se DABS quiser contorno, é um token novo.
- `--line` (divisores) é decorativo: 1,47 / 1,50 sobre `--ground`. Nenhum controlo depende só dele
  para ser encontrado (os botões têm fundo `--panel`).
- Foco: 2 px em `--lamp`; dentro de uma carta usa a tinta da carta (o âmbar sobre `#E9DFC9` daria 1,7).

## Encontrado e não resolvido

- 2026-10-06: **nunca testado contra um D1 remoto.** Só o D1 local do `vite dev` (miniflare) e um
  D1 simulado sobre libSQL nos testes. No primeiro deploy: migrar e semear no primeiro pedido,
  e confirmar que o `defer_foreign_keys` do lote de migrações é aceite pelo D1 real.
- 2026-10-06: **a atomicidade do `db.batch` no D1 remoto** (importação) está só provada em
  libSQL e no D1 local; o D1 documenta o lote como atómico, mas não foi exercitado em produção.
- 2026-10-06: **limites do D1**: 100 parâmetros por consulta (a semente de material e `loadJoins`
  já vão em blocos); uma consulta nova com listas longas tem de fazer o mesmo.
- 2026-09-26: **o material não tem export.** As cartas fazem round-trip por `/api/export.yaml`;
  o material acrescentado na UI só existe na base de dados.
- 2026-09-26: **acesso por teclado não auditado** contra os estilos atuais. Fica para o novo aspeto.
- `docs/mockups/` usa nomes genéricos de material (da passagem pela Escarpa). Só servem para layout.

## Registo

### 2026-10-06 — 188 cartas experimentais e curadoria das de arranque

- Importadas pelo `/import` em produção (Chrome, sessão de DABS): 188 novas, 0 erros; a
  biblioteca passou de 32 para 220. Foco pedido por DABS: sampling experimental (MPC One+,
  Circuit Rhythm, SK-1, Bitwig) e cordas, com uma parte fora desses dois pilares. Tags novas:
  `experimental` (em todo o lote), `chops`, `resampling`, `tuning`.
- Curadoria aprovada por DABS, feita no editor de cartas e confirmada no D1: a ideia Volca Keys →
  French Toast perdeu `treatment`; "Synth Wah on a source…" perdeu `constraint`; "Play it at half
  speed…", "Use it as the intro only…", "The loudest thing…" e "Build the whole thing from one
  bar." ganharam `constraint` (eram só technique/arrangement e nunca saíam); arquivadas "Mic it
  from three metres…" e "Two instruments at once…" (repetiam outras).
- Cliques "perdidos" no editor: **não é bug da app.** Investigado no mesmo dia. O separador do
  Claude in Chrome está escondido (`visibilityState: hidden`) e um clique dado logo a seguir a
  `navigate` não chega à página: zero `pointerdown`. Com uma captura de ecrã antes do clique, o
  mesmo clique chega e o editor reage bem (classe `on`, frase dos tipos, `change`). As
  coordenadas também mudam com o tamanho da janela. Para agentes: depois de `navigate`, tirar
  uma captura e confirmar o estado antes de gravar.
- Risco teórico, não observado: um clique antes de a hidratação acabar (~200 ms depois do
  carregamento) muda a checkbox sem passar por `toggleKind`, e o estado e a checkbox ficam
  dessincronizados. Não se corrige por agora; os sintomas seriam uma checkbox marcada com a
  pílula sem cor.

### 2026-10-06 — a skill de cartas lê a app publicada

- `.claude/skills/sotao-cards/scripts/cards.mjs` lê o material e as cartas do D1 de produção
  (`wrangler d1 execute --remote`, só leitura, o wrangler da app corrido pelo node, sem shell).
  Se falhar, avisa com o motivo do wrangler e usa o seed; `--seed` força o seed.
- Verificado: `vocab`, `existing` e `check` contra produção (3 corridas seguidas); a falha forçada
  (token inválido) cai no seed com a mensagem do wrangler.
- O lote `imports/2026-09-22-stairs-and-winter.yaml` (14 cartas novas, 0 erros) nunca foi
  importado.

### 2026-10-06 — troca de modo e três cartas em ecrã grande (relatado por DABS)

- **Troca One/Three:** a página não remonta ao mudar `?mode=`, por isso a tiragem do modo anterior
  ficava no ecrã com o formato do outro. Agora cada modo guarda e repõe a sua tiragem, histórico e
  trancas (`draw/+page.svelte`).
- **Três cartas:** a partir de 1100 px ficam lado a lado (fonte · tratamento · restrição), texto em
  cima, 34/28/24 px. Abaixo disso empilham: cada carta tem pelo menos a altura do seu texto, a
  sobra divide-se mais para as longas, e se não couberem a zona das cartas faz scroll (o Draw fica).
  O resumo dos filtros é uma linha até 1100 px.
- Verificado: 12 tiragens de três cartas em 390, 1024, 1100, 1280 e 1920 px, sem texto cortado nem
  scroll; troca One → Three → One → Three em 1920 e 1440 px.

### 2026-10-06 — D1 em vez de Turso (branch `sotao-d1`)

- Um módulo, dois drivers: `src/lib/server/db/index.ts` exporta `db` como proxy para a instância
  escolhida por `migrateDb(d1?)`, que o hook chama no primeiro pedido. Com o binding `DB`
  (produção e `vite dev`, via proxy da plataforma) usa `drizzle-orm/d1`; sem ele (Vitest) abre
  libSQL com `DATABASE_URL`. O `@libsql/client` carrega-se por `import()` só nesse caminho.
- `applyMigrations` recebe uma interface (`exec`, `lastWhen`, `batch`): adaptador libSQL
  (`client.migrate`) e adaptador D1 (`d1.batch`, atómico, com `PRAGMA defer_foreign_keys`).
  Uma só via de migração: a app migra-se; não se usa `wrangler d1 migrations apply`.
- Chaves estrangeiras: o D1 impõe-nas sempre; o `PRAGMA foreign_keys = ON` só corre em libSQL.
- A importação deixou de usar `db.transaction` (o D1 não suporta): é um `db.batch`, atómico nos
  dois drivers. Teste novo: uma carta inválida no lote não deixa nada escrito.
- `DATABASE_URL` e `DATABASE_AUTH_TOKEN` saíram do `.env.example`, do `drizzle.config.ts` e dos
  scripts `db:push`/`db:migrate`/`db:studio` (já não há URL). Só os testes usam `DATABASE_URL`
  (posto por `vite.config.ts`). O D1 local fica em `.wrangler/state`.
- **Dependência nova:** `@cloudflare/workers-types` 5.20261006.1 (devDependency, tipos de
  `D1Database`; licença MIT/Apache-2.0; alternativa: uma interface mínima escrita à mão, que
  deixaria de acompanhar a API do D1). Importada só como tipo, sem tipos globais.
- Encontrado ao testar contra o D1 local: 100 parâmetros por consulta. A semente do material
  (47 linhas x 6 colunas) e `loadJoins` passaram a inserir/consultar em blocos.
- Verificado: `scripts/check`; `vite dev` com `.wrangler/state` vazio (migrações e semente
  correram: 47 itens de material, 32 cartas); tiragens; importação por `/import` (pré-visualizar e
  gravar: 33 cartas); apagar material usado por uma carta é recusado (`FOREIGN KEY constraint
failed`); `wrangler deploy --dry-run`.

### 2026-10-06 — aspeto G implementado (branch `sotao-look-g`)

- Tokens de `layout.css` reescritos segundo `LOOK.md`; nomes e comentários do sea fog removidos.
  Tokens que já não cabiam saíram (`--rail`, `--line-soft`, `--text-faint`, `--text-muted`,
  `--accent`, `--fav-*`, `--chip-*`, `--tag-line`, `--danger-line`...). Entram `--card-<tipo>`,
  `--card-<tipo>-ink`, `--ink`, `--lamp-ink`, `--shadow-pop`; `--kind-<tipo>` passa a ser o tipo como texto.
- Temas: Auto / Dark / Light. Auto cai para Dark. Valores guardados mapeados: `night`/`dark` ->
  `dark`, `morning`/`light` -> `light`, `system` -> `auto`.
- **Dependências novas** (versão 5.3.0 fixa, licença SIL OFL 1.1; as três antigas saíram):
  - `@fontsource/bricolage-grotesque`: texto, títulos e botões do G; servida pela app, sem
    pedidos a terceiros. Alternativa: a variável do Google Fonts (pede à Google em runtime).
  - `@fontsource/courier-prime`: rótulos, slugs e contagens do G. Alternativa: `ui-monospace`
    do sistema (muda de aparelho para aparelho).
- Ícones redesenhados (`npm run icons`): fundo escuro, carta `#E9DFC9` inclinada, barra `--lamp`.
- Mudanças de comportamento: nenhuma intencional. O botão Draw diz "Draw two" / "Draw one" com
  1 / 2 cartas trancadas no Three (rótulo derivado de `locked`).
- **Não visto num ecrã.** Verificado: `scripts/check`, respostas 200 dos seis ecrãs, ausência das
  fontes e tokens antigos no HTML/CSS. Falta: ver telemóvel e largo, escuro e claro, offline.

### 2026-10-06 — propostas de aspeto; gate 2: G

- **Gate 2 aprovado:** DABS escolheu **G · Baralho suave**, "por enquanto". Regras em `docs/LOOK.md`.

- **Segunda ronda** (DABS gostou mais de A e C e pediu mais hipóteses): **D · Cartolina** (a ficha
  da A em cartolina de cor por tipo), **E · Carta de jogar** (carta branca, índice nos cantos,
  cor só no naipe), **F · Fichário** (ficha de catálogo com separadores por tipo, claro por
  defeito), **G · Baralho suave** (a C com cores baixas e o fundo quente da A). Só os dois
  ecrãs de tirar, no mesmo canvas.

- Três direções, cada uma com tirar uma, tirar três e a lista de cartas, para telemóvel, com
  ajuste escuro/claro: **A · Lamparina** (ficha de cartolina, Courier Prime + Archivo, botão
  âmbar), **B · Fita-cola** (fita de papel numa mesa de mistura, Permanent Marker + Chivo),
  **C · Baralho** (cartas de cor chapada por tipo, Bricolage Grotesque + Azeret Mono).
- Canvas: https://claude.ai/artifact/G3HZ1K6AzNKup4fWCwcUzh (privado). Fontes em
  `docs/look-2026-10-06/` (`.dc.html`, só abrem no canvas).
- Nenhuma usa as fontes da Escarpa nem as do spec original. Contrastes não medidos: medem-se
  na direção escolhida, como em §12.
- Proposta, não decisão: muda a navegação para uma barra de baixo e o modo para "One card /
  Three". DABS decide com o mockup.

### 2026-10-06 — saída da Escarpa Audio para o dabs-apps

- **Movido** do branch `sotao` do `escarpa-audio` por `git subtree split` + `add`, com a
  história toda, incluindo a do repo original `davidbrassimoes/sotao-cards`. Decidido por
  DABS: é uma ferramenta pessoal, não encaixa na marca nem no repo da empresa. O branch
  nunca foi integrado e o ADR-027 de lá morre com ele.
- **Nomes reais no git** (decidido por DABS): `seed/gear.ts` e `seed/cards.yaml` são o rig
  real; os exemplos genéricos e o par `*.local.*` saíram. O exemplo de importação, os
  placeholders do ecrã Gear e o §7 do spec voltaram aos nomes reais.
- **Marcas da Escarpa removidas** (wordmark e carimbo no bloco About). O resto do sea fog fica
  até haver novo aspeto.
- Mantido da passagem pela Escarpa: Cloudflare Workers + Turso, migrações empacotadas, o
  nome _Sótão — studio cards_, temas Auto/Morning/Night.
- `.env`, `local.db` e `imports/` copiados das pastas antigas; estão no `.gitignore`.
- `scripts/check` e job `sotao` na CI. Verificado: `scripts/check` verde; servidor de dev
  local, login e os cinco ecrãs a responder 200 com o material real.
