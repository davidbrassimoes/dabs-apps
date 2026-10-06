# Notas — gota

> Uma página que a próxima sessão lê em vez de repetir uma conversa. Mais recente no topo.
> Factos e decisões, não narrativa. Uma decisão que mude uma regra vai para um ADR.

## Para a próxima sessão

- As 5 fases do `docs/SPEC.md` estão implementadas e com testes; CI verde.
- **À espera de uso real:** a app só foi testada com as fotografias de calibração. O
  próximo abastecimento é o primeiro teste a sério. Se o talão ou o conta-km falharem,
  DABS põe as fotografias em `test-photos/` (fora do git) e afinam-se os parsers com
  `SamplePhotoOcrDumpTest` (ver `docs/SPEC.md`, "OCR do talão").
- O Pixel 8 tem o build de debug de `d1cb1b8` (instalado 2026-10-05 23:20). O
  `versionCode` é sempre 1: comparar pela hora de instalação
  (`adb shell dumpsys package pt.dabs.fueltracker | grep lastUpdateTime`).
- Verificar: `scripts/check` (ou `scripts/check gota` da raiz).

## Dados pessoais

| Dado | Finalidade | Retenção | Onde fica |
|---|---|---|---|
| Abastecimentos (data, km, litros, preços, posto, NIF do posto, ATCUD) | Consumo e custos | Até o utilizador apagar | Room, só no telemóvel; export CSV manual |
| Fotografia do talão em resolução máxima | Ler o texto por OCR | Apagada logo após a leitura | Cache da app |
| Fotografias de calibração | Afinar os parsers | Enquanto forem úteis | `test-photos/` no PC, fora do git |

## Encontrado e não resolvido

- 2026-10-06: `test-photos/` perdeu-se ao apagar a pasta antiga `repos/gota` (está no
  `.gitignore`); repostas do rolo da câmara do Pixel 8 as duas que tinham dump em
  `ocr-dump/` (`PXL_20261004_194004489.MP.jpg`, `PXL_20261004_194017500.jpg`). Se havia
  mais, continuam no telemóvel (ex.: `PXL_20261004_194038502.jpg`).

## Registo

### 2026-10-06 — mudança para o dabs-apps

Movida com a história de `davidbrassimoes/gota` (agora arquivado). Funcionalidades
completas segundo o SPEC; próximo passo é validar com dados reais.
