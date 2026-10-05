# products/

Um produto por pasta. **O produto é dono do seu build**: a raiz não assume toolchain
nenhuma, e dois produtos podem usar stacks diferentes sem se pisarem.

## O que cada produto tem de ter

| Ficheiro | O que é |
|---|---|
| `CLAUDE.md` | Até 40 linhas: o que é, stack, comandos, regras duras **deste** produto, o que ler primeiro. Tem prioridade sobre o da raiz. |
| `BRIEF.md` | De `templates/brief.md` |
| `SPEC.md` | De `templates/spec.md`. Com "Estado: aprovado" antes de haver código. |
| `NOTES.md` | De `templates/notes.md`. A página que a próxima sessão lê. |
| `scripts/check` | Executável (bash). Corre tudo o que verifica o produto e **imprime só falhas**. Sai com 0 quando passa. |
| `.gitignore` | As pastas de build da toolchain deste produto |
| `RELEASE.md` | De `templates/release-checklist.md`, quando houver uma release |

E em `projects/<nome>.md`, o estado do projeto (dono, decisões, por decidir).

## `scripts/check` de um produto

É a peça que mais tokens poupa. Regras:

- Corre em bash (no Windows, Git Bash), a partir da pasta do produto.
- Modo silencioso da toolchain (`-q`, `--quiet`, `--console=plain`, `--no-progress`).
- Quando tudo passa, imprime uma linha ou nada. Quando falha, imprime a falha e nada mais.
- Sai com código diferente de 0 em qualquer falha.
- Não instala nada; assume o ambiente do `CLAUDE.md` do produto.

Exemplo (Gradle):

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew -q --console=plain testDebugUnitTest lintDebug 2>&1 | grep -vE '^(> Task|BUILD|$)' || true
test "${PIPESTATUS[0]}" -eq 0
```

Exemplo (pnpm):

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
pnpm -s typecheck && pnpm -s lint --quiet && pnpm -s test --silent
```

## Código partilhado

Quando dois produtos precisam do mesmo, move-se para `packages/<nome>/` (ou `modules/`,
conforme a stack) com o seu próprio `scripts/check`. Antes disso, não se antecipa: uma
abstração para um produto só é custo.
