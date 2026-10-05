#!/usr/bin/env bash
#
# Na CI: este push ou pull request tocou em algum dos caminhos dados?
#
# Escreve `changed=true` ou `changed=false` em $GITHUB_OUTPUT, para um passo ler com
# `if: steps.<id>.outputs.changed == 'true'`.
#
#   scripts/changed.sh products/<nome> packages/<x> .github
#
# Um repo com várias toolchains não deve construir uma por causa de uma alteração à outra.
# Isto fá-lo numa dúzia de linhas legíveis em vez de uma action do marketplace, o que o
# mantém óbvio e fora da supply chain.
#
# Quando não dá para saber a base (branch nova, force-push, primeiro commit), a resposta é
# "changed": uma build a mais é mais barata do que uma regressão a menos, e um filtro que
# falhasse fechado escondia a avaria em silêncio.

set -euo pipefail

say() {
  echo "changed=$1" >>"${GITHUB_OUTPUT:-/dev/stdout}"
  echo "$2"
  exit 0
}

if [ "$#" -eq 0 ]; then
  echo "uso: changed.sh <caminho> [caminho...]" >&2
  exit 2
fi

if [ "${GITHUB_EVENT_NAME:-}" = "pull_request" ]; then
  branch="${GITHUB_BASE_REF:-}"
  base=""
  if [ -n "$branch" ] && git fetch --quiet origin "$branch" 2>/dev/null; then
    base="$(git merge-base FETCH_HEAD HEAD 2>/dev/null || true)"
  fi
else
  base="${GITHUB_EVENT_BEFORE:-}"
  case "$base" in
    0000000000000000000000000000000000000000 | "") base="" ;;
  esac
fi

if [ -z "$base" ]; then
  say true "Sem commit de base para comparar: corre tudo."
fi

if ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
  say true "O commit de base $base não está neste clone: corre tudo."
fi

files="$(git diff --name-only "$base" HEAD)"

if [ -z "$files" ]; then
  say false "Nenhum ficheiro alterado."
fi

for path in "$@"; do
  # O caminho em si ou algo por baixo, ancorado ao início: "tools" nunca apanha "products/x/tools".
  if echo "$files" | grep -qE "^${path}(/|$)"; then
    say true "Tocou em '$path': corre."
  fi
done

echo "Ficheiros alterados:"
echo "$files" | sed 's/^/  /'
say false "Nada em: $*"
