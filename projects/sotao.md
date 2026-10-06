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
5. 2026-10-06, DABS: fica PWA por agora; portar para outras plataformas não está em cima da mesa.
6. 2026-10-06, DABS: deploy nas mesmas contas Cloudflare e Turso da Escarpa Audio, num Worker próprio.

## Por decidir

| # | Pergunta | Quem decide | Bloqueia |
|---|---|---|---|
| 1 | O novo aspeto (mockup) | DABS, gate 2 | Trabalho de UI |
| 2 | Domínio próprio ou só `workers.dev` | DABS | Nada; publicação é gate 3 |

## Próximos passos

- [ ] DABS escolhe uma das três propostas (`products/sotao/NOTES.md`, 2026-10-06) → gate 2
- [ ] Primeiro deploy (gate 3): criar base Turso, `wrangler secret put` para os quatro
      secrets, `npm run deploy`, testar o `PRAGMA foreign_keys` (ver `NOTES.md`)

## Histórico

- 2026-09-22: fase 1 (M0–M5) construída em `davidbrassimoes/sotao-cards`.
- 2026-09-26: trazido para o `escarpa-audio` num branch, adaptado às regras da empresa.
- 2026-10-06: movido para o `dabs-apps`; nomes reais no git; marcas da Escarpa removidas.
