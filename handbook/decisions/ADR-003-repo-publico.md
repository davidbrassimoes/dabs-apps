# ADR-003 — O repo é público

**Estado:** aceite
**Data:** 2026-11-08
**Decide:** DABS

## Contexto

No plano Free do GitHub, um repo privado não tem proteção de branch nem rulesets (a API
responde 403) e partilha 2000 minutos de Actions por mês com os outros repos privados da
conta. Em outubro a conta gastou 2064. Num repo público as duas coisas são grátis.

A revisão antes de publicar encontrou um produto pessoal que não podia ficar à vista,
porque descrevia a vida profissional de outra pessoa. Saiu do repo, e do histórico, para
um repo só local. O repo privado antigo foi apagado do GitHub. Há uma cópia em mirror,
só local, fora do GitHub.

## Decisão

- `davidbrassimoes/dabs-apps` é público desde 2026-11-08, com histórico reescrito.
- Não entra no repo nada sobre terceiros que eles não pusessem em público: nomes,
  empregadores, moradas, NIF, matrículas, dinheiro, documentos reais. Exemplos são
  fictícios (`000000000`, `Rua Exemplo`), como a regra dura 3 já pede.
- Um produto cujo propósito, mesmo sem dados, exponha a vida privada de alguém não vive
  aqui. Vive num repo só local, sem remote.
- Material de referência real (PDFs, folhas de cálculo, fotos com pessoas ou matrículas)
  fica ignorado pelo git, nunca no histórico.

## Razões

- Proteção de branch e minutos ilimitados sem pagar o GitHub Pro.
- Tirar um ficheiro num commit novo não chega: o histórico público fica indexado e em cache.

## Consequências

- Qualquer brief novo passa a pergunta: "isto pode estar em público?". Se não, o produto
  nasce fora deste repo.
- Perdeu-se no GitHub o histórico dos PRs #1 a #43. Os commits continuam aqui.
- O ADR-002 saiu com o produto a que se aplicava; o número fica retirado.

## O que substitui

Nada.
