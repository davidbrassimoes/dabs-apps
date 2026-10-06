# sotao

**Dono:** DABS
**Estado:** em construção (fase 1 e aspeto G feitos; falta o primeiro deploy)
**Destino:** pessoal. Se mudar, ver ADR-001 (5).
**Pasta:** `products/sotao/`

## Onde está

*Sótão — studio cards*: baralho de ideias para o estúdio, SvelteKit em Cloudflare Workers +
Turso, instalável e offline. A fase 1 está construída e testada, nunca publicada. O aspeto G passou o gate 2;
a seguir é implementá-lo, depois o primeiro deploy (gate 3).

## Decisões do projeto

1. 2026-10-06, DABS: o Sótão sai da Escarpa Audio (branch `sotao`, nunca integrado) e entra
   aqui, com história. Não encaixa na marca da Escarpa nem no repo da empresa.
2. 2026-10-06, DABS: parte-se da versão do branch (Workers + Turso, nome novo), não do repo
   original `sotao-cards`.
3. 2026-10-06, DABS: nomes reais de material entram no git.
4. 2026-10-06, DABS: o Sótão ganha um aspeto próprio. Até lá fica o sea fog da Escarpa, provisório.
5. 2026-10-06, DABS: fica PWA por agora; portar para outras plataformas não está em cima da mesa.
6. 2026-10-06, DABS: deploy nas mesmas contas Cloudflare e Turso da Escarpa Audio, num Worker próprio.
7. 2026-10-06, DABS, gate 2: aspeto **G · Baralho suave** "por enquanto" (`products/sotao/docs/LOOK.md`).
8. 2026-10-06, DABS: **Cloudflare D1 em vez de Turso** (substitui a parte Turso da decisão 6). Porquê: uma conta só, sem serviço nem token extra, e o D1 tem as chaves estrangeiras sempre ligadas, o que fecha o risco do `PRAGMA foreign_keys` sobre HTTP. Alternativa pesada e posta de lado por agora: VPS com Node + ficheiro SQLite (zero mudanças de código, mas operação contínua). Custo: trocar o driver e passar a única transação (importar) a `batch`.

## Por decidir

| # | Pergunta | Quem decide | Bloqueia |
|---|---|---|---|
| 2 | Domínio próprio ou só `workers.dev` | DABS | Nada; publicação é gate 3 |

## Próximos passos

- [x] Gate 2: aspeto G escolhido (2026-10-06)
- [x] Aspeto G implementado e verificado em capturas, 390 e 1280 px, dois temas (2026-10-06)
- [ ] Trocar Turso por D1 (decisão 8)
- [ ] Primeiro deploy (gate 3): `wrangler secret put` para `APP_PASSWORD` e `APP_SECRET`, `npm run deploy`,
      testar que apagar material usado por uma carta falha

## Histórico

- 2026-09-22: fase 1 (M0–M5) construída em `davidbrassimoes/sotao-cards`.
- 2026-09-26: trazido para o `escarpa-audio` num branch, adaptado às regras da empresa.
- 2026-10-06: movido para o `dabs-apps`; nomes reais no git; marcas da Escarpa removidas.
- 2026-10-06: gate 2 (aspeto G) e implementação no `main`.
