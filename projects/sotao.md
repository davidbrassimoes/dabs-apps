# sotao

**Dono:** DABS
**Estado:** em construção (fase 1 feita; novo aspeto por desenhar)
**Destino:** pessoal. Se mudar, ver ADR-001 (5).
**Pasta:** `products/sotao/`

## Onde está

*Sótão — studio cards*: baralho de ideias para o estúdio, SvelteKit em Cloudflare Workers +
Turso, instalável e offline. A fase 1 está construída e testada, nunca publicada. O próximo
gate é o **mockup do novo aspeto** (gate 2).

## Decisões do projeto

1. 2026-10-06, DABS: o Sótão sai da Escarpa Audio (branch `sotao`, nunca integrado) e entra
   aqui, com história. Não encaixa na marca da Escarpa nem no repo da empresa.
2. 2026-10-06, DABS: parte-se da versão do branch (Workers + Turso, nome novo), não do repo
   original `sotao-cards`.
3. 2026-10-06, DABS: nomes reais de material entram no git.
4. 2026-10-06, DABS: o Sótão ganha um aspeto próprio. Até lá fica o sea fog da Escarpa, provisório.

## Por decidir

| # | Pergunta | Quem decide | Bloqueia |
|---|---|---|---|
| 1 | O novo aspeto (mockup) | DABS, gate 2 | Trabalho de UI |
| 2 | Portar para outras plataformas (app nativa, desktop)? Se sim, quais e quando. Pesa no desenho do novo aspeto e no stack | DABS | O spec do novo aspeto |
| 3 | Que conta Cloudflare (e Turso) recebe o Worker: a de DABS pessoal ou outra. A da Escarpa já não serve | DABS | Primeiro deploy |
| 4 | Domínio próprio ou só `workers.dev` | DABS | Nada; publicação é gate 3 |

## Próximos passos

- [ ] Brief curto do novo aspeto (e da questão das plataformas) → mockup → gate 2
- [ ] Primeiro deploy (gate 3): criar base Turso, `wrangler secret put` para os quatro
      secrets, `npm run deploy`, testar o `PRAGMA foreign_keys` (ver `NOTES.md`)

## Histórico

- 2026-09-22: fase 1 (M0–M5) construída em `davidbrassimoes/sotao-cards`.
- 2026-09-26: trazido para o `escarpa-audio` num branch, adaptado às regras da empresa.
- 2026-10-06: movido para o `dabs-apps`; nomes reais no git; marcas da Escarpa removidas.
