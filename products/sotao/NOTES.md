# Notas — sotao

> Uma página que a próxima sessão lê em vez de repetir uma conversa. Mais recente no topo.
> Factos e decisões, não narrativa. Uma decisão que mude uma regra vai para um ADR.

## Para a próxima sessão

- **Fase 1 construída** (M0–M5 de `docs/IMPLEMENTATION.md` §15), 114 testes. Nunca publicada.
- **Próximo: o novo aspeto.** Mockup primeiro, gate 2 com DABS. O aspeto atual é o sea fog da
  Escarpa Audio, provisório (`src/routes/layout.css`). Antes de desenhar, saber se o porte
  para outras plataformas está em cima da mesa (`projects/sotao.md`, por decidir).
- Verificar: `scripts/check` (desta pasta) ou `scripts/check sotao` (da raiz).

## Dados pessoais

| Dado                            | Finalidade                           | Retenção               | Onde fica                                  |
| ------------------------------- | ------------------------------------ | ---------------------- | ------------------------------------------ |
| Password única (`APP_PASSWORD`) | Proteger a app de um só utilizador   | Enquanto a app existir | `.env` local; secret do Worker em produção |
| Cookie de sessão assinado       | Manter a sessão aberta (dura um ano) | Um ano ou até sair     | Browser do utilizador                      |

Cartas, material e histórico de tiragens não são dados pessoais de terceiros.

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

### 2026-10-06 — propostas de aspeto (gate 2 pendente)

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
