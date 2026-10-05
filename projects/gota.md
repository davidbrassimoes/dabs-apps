# gota

**Dono:** DABS
**Estado:** em construção
**Destino:** pessoal. Se mudar, ver ADR-001 (5).
**Pasta:** `products/gota/`

## Onde está

App Android de registo de abastecimentos e consumo, totalmente offline. Spec em
`products/gota/docs/SPEC.md` (convenção anterior ao template; mantém-se). Movida para este
workspace a 2026-10-05 com a história do repo `davidbrassimoes/gota`. Último trabalho:
marca do posto e tipo de combustível (`d1cb1b8`).

## Decisões do projeto

1. 2026-10-05, DABS: entra no `dabs-apps` como primeiro produto; o repo antigo arquiva-se
   quando a CI aqui estiver verde.

## Por decidir

| # | Pergunta | Quem decide | Bloqueia |
|---|---|---|---|
| 1 | Nome definitivo e `applicationId` (hoje `pt.dabs.fueltracker`) | DABS | Só uma eventual publicação |

## Próximos passos

- [x] CI verde em `products/gota` (`scripts/check gota`) (2026-10-06)
- [x] Repo antigo `davidbrassimoes/gota` arquivado e pasta local removida (2026-10-06)
- [ ] `NOTES.md` do produto criado de `templates/notes.md` na próxima fatia

## Histórico

- 2026-10-05: movido para `dabs-apps`.
- 2026-10-06: CI verde; repo antigo `davidbrassimoes/gota` arquivado (só leitura).
