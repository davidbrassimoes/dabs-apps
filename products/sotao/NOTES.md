# Notas — sotao

> Uma página que a próxima sessão lê em vez de repetir uma conversa. Mais recente no topo.
> Factos e decisões, não narrativa. Uma decisão que mude uma regra vai para um ADR.

## Para a próxima sessão

- **Fase 1 construída** (M0–M5 de `docs/IMPLEMENTATION.md` §15), 114 testes. Nunca publicada.
- **Aspeto G no `main`** (2026-10-06). Visto em capturas headless a 390 e 1280 px, nos dois
  temas; atalhos de teclado, offline e telemóvel real por ver. Em ecrã largo o texto da carta
  fica pequeno (o escalonamento por comprimento aplica-se a todas as larguras): afinar.
- **Próximo: primeiro deploy (gate 3)** nas contas da Escarpa. DABS cria a base Turso e os
  quatro secrets (`wrangler secret put`).
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

- 2026-09-26: **nunca testado contra Turso real.** `wrangler deploy --dry-run` empacota o
  cliente web do libSQL, sem binding nativo. Nada falou ainda com uma base de dados real a
  partir de um Worker.
- 2026-09-26: **`PRAGMA foreign_keys = ON` sobre HTTP.** `migrateDb` liga-o uma vez por
  isolate, mas no protocolo HTTP do Turso cada pedido pode ser uma ligação nova. Se não
  pegar, os cascades e o RESTRICT que protege material referenciado deixam de funcionar sem
  erro. Testar no primeiro deploy: apagar material que uma carta usa tem de falhar.
- 2026-09-26: **o material não tem export.** As cartas fazem round-trip por `/api/export.yaml`;
  o material acrescentado na UI só existe na base de dados.
- 2026-09-26: **acesso por teclado não auditado** contra os estilos atuais. Fica para o novo aspeto.
- `docs/mockups/` usa nomes genéricos de material (da passagem pela Escarpa). Só servem para layout.

## Registo

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
