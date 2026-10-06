# gota

**Dono:** DABS
**Estado:** funcionalidades completas; à espera de validação no próximo abastecimento real
**Destino:** pessoal. Se mudar, ver ADR-001 (5).
**Pasta:** `products/gota/`

## Onde está

App Android de registo de abastecimentos e consumo, totalmente offline. Spec em
`products/gota/docs/SPEC.md` (convenção anterior ao template; mantém-se). Movida para este
workspace a 2026-10-05 com a história do repo `davidbrassimoes/gota`. Último trabalho:
marca do posto e tipo de combustível (`d1cb1b8`). As 5 fases do SPEC estão feitas, mas a app
só foi testada com as fotografias de calibração do OCR; o build de `d1cb1b8` está no Pixel 8.

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
- [x] `NOTES.md` do produto criado (2026-10-06)
- [ ] Usar no próximo abastecimento real; se o OCR falhar, guardar as fotografias em
      `products/gota/test-photos/` e afinar os parsers

## Histórico

- 2026-10-05: movido para `dabs-apps`.
- 2026-10-06: CI verde; repo antigo `davidbrassimoes/gota` arquivado (só leitura).
- 2026-10-06: funcionalidades completas; estado passa a "à espera de validação real".
