# Sótão — studio cards

Tira uma carta quando DABS se senta no estúdio e não sabe por onde começar: uma ideia, ou três
cartas parciais (fonte, tratamento, restrição) que se combinam. Mais um gestor de cartas, um
inventário do material e importação YAML. SvelteKit 2 + Svelte 5 em Cloudflare Workers,
Cloudflare D1, instalável no telemóvel e utilizável offline. Estado: `../../projects/sotao.md`.

**`docs/IMPLEMENTATION.md` é o spec** (em inglês, do repo original). O comportamento, os
formatos e os números são vinculativos; o topo diz que secções foram substituídas.
`docs/mockups/` é anterior a tudo e só serve para layout.

## Antes de mudar alguma coisa

- **`src/lib/draw.ts` é puro**: recebe cartas, filtros e as tiragens recentes e devolve
  cartas. Sem base de dados, sem `fetch`, nada de `server/`. Corre no browser (§3, §6).
- `draw.ts`, `cardId.ts`, `pool.ts` e `outbox.ts` ficam fora de `server/` porque correm no cliente.
- **Fora de âmbito na fase 1** (§2): pesos nas tiragens, spreads configuráveis, notas por
  carta, anexos, segundo utilizador. Um utilizador, uma password, sem tabela de users (§10).
- **Material sempre ligado não tem linha** (mesa, monitores, auscultadores, portátil). Contestar antes de acrescentar.
- **Nomes reais entram no git.** `seed/gear.ts` e `seed/cards.yaml` são o rig e as cartas de
  arranque verdadeiros. Os slugs são o vocabulário das importações: mudar um parte as cartas que o usam.
- **Cores só como tokens** em `src/routes/layout.css`, nunca literais num componente. O
  aspeto é o de `docs/LOOK.md` (G, aprovado no gate 2).

## Comandos (de `products/sotao/`)

|                       |                                                                                                                                                |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `npm run dev`         | Servidor de dev. Precisa de `.env` com `APP_PASSWORD` e `APP_SECRET` (ver `.env.example`). A base de dados é um D1 local, em `.wrangler/state` |
| `scripts/check`       | Tipos, lint, testes (mínimo 100) e build. **Correr antes de dar algo por feito**                                                               |
| `npm test`            | Vitest, com base de dados em memória                                                                                                           |
| `npm run deploy`      | Build e `wrangler deploy`. É publicação: gate 3                                                                                                |
| `npm run db:generate` | Migração nova a partir de `schema.ts`; é commitada e empacotada no build                                                                       |
| `npm run icons`       | Redesenha os ícones de `static/` a partir da paleta                                                                                            |

As migrações vão no bundle, não se leem do disco (`src/lib/server/db/migrations.ts`): um
Worker não tem disco. Um teste prende o bundle ao leitor do próprio drizzle.
A app migra e semeia a base de dados sozinha no primeiro pedido, em D1 e em libSQL (testes): não
correr `wrangler d1 migrations apply`. Os testes usam libSQL em memória; D1 só existe no Worker e no `vite dev`.

A skill de gerar cartas está na raiz, `.claude/skills/sotao-cards/`. No fim da sessão, `NOTES.md`.
