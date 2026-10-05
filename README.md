# workspace-template

Esqueleto de um workspace para desenvolvimento agêntico com Claude Code: um monorepo por
entidade (uma empresa, uma pessoa), um produto por pasta, cada produto com a sua
toolchain, e um handbook curto que os agentes leem só na parte que o trabalho pede.

Destilado de dois workspaces que funcionam: `webdabs` (web, pnpm) e `escarpa-audio`
(JUCE/C++ e .NET lado a lado). O que está aqui é o que os dois têm em comum; o que
difere entre eles (stack, regras de produto, marca) fica por preencher.

## Princípio de organização

**Um workspace por entidade que detém o código**, não por plataforma nem por linguagem.
A entidade decide quem aprova os gates, que regras legais e de marca se aplicam, quem tem
acesso e para onde vai um produto se for vendido. Um produto que mude de dono muda de
workspace (`git subtree split` preserva a história).

## Como instanciar

1. **Criar o repo a partir deste template** (GitHub: "Use this template"), privado, na
   conta ou organização da entidade.
2. **Substituir os marcadores** `<...>` em `CLAUDE.md` e no handbook. Procurar com
   `grep -rn '<[A-Z]' CLAUDE.md handbook templates`.
3. **Preencher `handbook/00-manifesto.md`.** São perguntas; não se inventa resposta. O que
   não se sabe fica escrito como "por decidir".
4. **Escrever o ADR-001** a partir de `handbook/decisions/ADR-000-template.md`: quem aprova
   cada gate, a língua dos documentos, a stack inicial (ou "por produto"), o modelo de
   trabalho a vários se houver mais de uma pessoa.
5. **Adicionar o primeiro produto** em `products/<nome>/` com `CLAUDE.md`, `SPEC.md`
   (de `templates/spec.md`), `NOTES.md` e `scripts/check` executável que corre tudo e
   imprime só falhas. Ver `products/README.md`.
6. **Um job de CI por produto** em `.github/workflows/ci.yml`, filtrado por caminho com
   `scripts/changed.sh`. O exemplo comentado mostra a forma.
7. **Rever `.claude/settings.json`**: acrescentar aos `deny` as pastas de build da
   toolchain escolhida e aos `allow` os comandos de verificação.
8. **Apagar este README** ou substituí-lo pelo do workspace.

## O que não é

Não é uma stack, nem um gerador de código, nem um plugin. É a forma de trabalhar: onde
ficam as decisões, como se passa trabalho entre agentes, onde pára para aprovação e como
se mantém o contexto pequeno.

## Manter o template vivo

Quando um workspace aprender algo que generaliza (um agente melhor, uma regra de sessão,
um item da barra de qualidade), traz-se para aqui por commit, e os outros workspaces
copiam quando lhes convier. Não há sincronização automática; cada workspace é autónomo.
