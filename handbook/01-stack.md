# Stack

## Regra base

**Cada produto escolhe a stack pelo seu gargalo, não por familiaridade**, e regista a
escolha num ADR curto. Um monorepo aguenta toolchains diferentes: cada `products/<nome>/`
tem o seu build, o seu `scripts/check` e o seu `.gitignore` de build. A raiz não assume
nenhuma.

Enquanto não houver um padrão entre produtos, esta página fica curta. Quando dois
produtos escolherem o mesmo por boas razões, isso passa a ser a stack por defeito para
esse tipo de produto, e escreve-se aqui.

## Por defeito, por tipo de produto

| Tipo | Stack por defeito | Decidido em |
|---|---|---|
| App Android | Kotlin, Jetpack Compose, Material 3, Hilt, Room; Gradle com version catalog; JDK 17 de alvo | ADR-001 (de `gota`) |
| App Android TV | Kotlin + OpenGL ES sobre `GLSurfaceView`, sem Compose no render | `products/santa-vibes/docs/decisoes/` |
| Desktop Windows, servidor Ubuntu | por decidir | |

## Quando desviar

Um desvio do defeito precisa de um ADR que nomeie **o gargalo medido** ou o requisito que
o defeito não cumpre. "Gosto mais" e "é mais moderno" não são razões. "Chato e estável"
ganha: APIs maduras e bem representadas nos dados de treino custam menos tokens e dão
menos erros.

## Dependências

- Uma linha a justificar cada dependência nova: porquê, licença, alternativa considerada.
  Fica no `SPEC.md` do produto, secção "Dependências e licenças".
- Licenças copyleft (GPL, AGPL) não entram em código que se distribui sem decisão
  escrita. LGPL redistribuída obriga a publicar a fonte correspondente no mesmo passo.
- Versões fixadas por catálogo ou lockfile, nunca soltas em ficheiros de build.
- Ações de CI fixadas por SHA completo, com a versão em comentário.

## Media e ficheiros grandes

Originais (fotografias, áudio, vídeo, ficheiros de design) **não entram no git**. Ficam em
`media/<produto>/` localmente, ignorados, com um `MEDIA.md` versionado que diz o que lá
está, de onde veio e com que licença. O armazenamento partilhado (bucket, disco) é decisão
por workspace e fica num ADR. Enquanto não existir, **os originais só existem num disco**,
e isso tem de estar escrito no `NOTES.md` do produto.

## Ambiente de desenvolvimento

O que cada pessoa precisa de ter instalado para correr `scripts/check` em cada produto.
Cada produto lista o seu no `CLAUDE.md`; aqui fica só o comum a todos.

- Git, bash (no Windows, Git Bash).
- Claude Code.
- <o resto, por produto>
