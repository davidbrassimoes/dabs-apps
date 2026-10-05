# ADR-001 — Decisões fundadoras do workspace

**Estado:** aceite
**Data:** 2026-10-05
**Decide:** DABS

## Contexto

As apps pessoais de DABS (`gota`, `santa-vibes`) viviam em repos soltos sem handbook,
agentes, CI nem `.claude/`. O que se partilhava com os outros workspaces (`webdabs`,
`escarpa-audio`) não era código, era forma de trabalhar, e isso só flui dentro de um repo.

## Decisão

1. **Um workspace para as apps pessoais**, a partir de `workspace-template`, com um produto
   por pasta e cada produto dono do seu build (`products/README.md`).
2. **DABS aprova os quatro gates.** Os gates continuam a existir: são o ponto em que o
   agente pára e DABS lê, mesmo sozinho.
3. **Língua.** Documentação em português de Portugal; código, identificadores e commits em
   inglês.
4. **Stack por produto.** A do `gota` (Kotlin, Compose, Material 3, Hilt, Room, Gradle com
   version catalog) passa a ser a stack por defeito para apps Android. A do `santa-vibes`
   entra quando ele entrar.
5. **Produto que vira produto muda de dono.** Uma app que passe a ser vendida ou distribuída
   em nome de uma entidade (Escarpa, Digital Obelisk) sai daqui por `git subtree split` para
   o workspace dela. Até lá não se fixa nada caro de mudar: nome definitivo, identificador
   de pacote, domínio.
6. **Os repos antigos arquivam-se**, não se apagam, depois de a CI aqui ficar verde para
   esse produto.

## Razões

- Handbook, agentes, `knowledge/` e `settings.json` só rendem partilhados num repo.
- O `escarpa-audio` já prova que um monorepo aguenta toolchains diferentes (ADR-006 dele).
- Decidir o destino comercial agora seria inventar; a regra 5 adia sem custo.

## Consequências

- `products/gota/` tem a história do repo `davidbrassimoes/gota` por subtree.
- Cada produto ganha `scripts/check`, um job na CI filtrado por caminho e um
  `projects/<nome>.md`.
- `handbook/01-stack.md` tem a linha Android preenchida.

## O que substitui

Nada; é o primeiro.
