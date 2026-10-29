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

# A base vem do payload do evento, que a CI dá sempre. Até 2026-10-29 vinha de um
# `git fetch` (falha sem credenciais, com `persist-credentials: false`) e de
# GITHUB_EVENT_BEFORE (o GitHub não define essa variável): corria sempre tudo.
# Numa PR, HEAD é o merge da PR na base, por isso a diferença para base.sha é a da PR.
base=""
event="${GITHUB_EVENT_PATH:-}"
if [ -n "$event" ] && [ -f "$event" ]; then
  case "${GITHUB_EVENT_NAME:-}" in
    pull_request) base="$(jq -r '.pull_request.base.sha // empty' "$event")" ;;
    push) base="$(jq -r '.before // empty' "$event")" ;;
  esac
fi
case "$base" in
  0000000000000000000000000000000000000000) base="" ;;
esac

if [ -z "$base" ]; then
  case "${GITHUB_EVENT_NAME:-}" in
    pull_request | push) echo "::warning::changed.sh sem commit de base num ${GITHUB_EVENT_NAME}: corre tudo." ;;
  esac
  say true "Sem commit de base para comparar: corre tudo."
fi

if ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
  echo "::warning::changed.sh: o commit de base $base não está neste clone (fetch-depth: 0?): corre tudo."
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
