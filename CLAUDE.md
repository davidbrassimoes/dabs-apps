# <NOME DO WORKSPACE> — instruções da raiz

Monorepo de <ENTIDADE: quem detém este código>, com agentes como forma principal de
trabalho. **O repo é a fonte de verdade**: decisões ficam aqui, em commits. Nunca
contradizer uma decisão commitada; muda-se com um commit que diga porquê.

Lê este ficheiro. Não leias mais nada até saberes qual é o trabalho.

## Onde estão as coisas

| Caminho | O que tem | Ler quando |
|---|---|---|
| `handbook/00-manifesto.md` | O que fazemos, para quem, o que recusamos, quem decide | Antes de um brief ou spec |
| `handbook/01-stack.md` | Stack por defeito e quando desviar | Qualquer código |
| `handbook/02-qualidade.md` | A barra de entrega | Antes de dar algo como feito |
| `handbook/03-ways-of-working.md` | Pipeline, agentes, gates, autonomia, trabalhar a vários | Antes de passar trabalho a outro agente |
| `handbook/decisions/` | ADRs, numerados e datados | Quando algo parece arbitrário |
| `handbook/knowledge/` | Aprendizagens entre produtos, uma por ficheiro; `README.md` é o índice | Antes de resolver algo que já resolvemos |
| `templates/` | Brief, spec, notas, estado de projeto, checklist de release, registo de obrigações | Começar qualquer um deles |
| `products/<nome>/` | Um produto, código incluído. Tem o seu `CLAUDE.md`, `SPEC.md`, `NOTES.md` e o seu build | Só o produto em que estás |
| `projects/<nome>.md` | Estado de cada produto: decisões, por decidir, próximos passos | Retomar um produto |
| `scripts/check` | Corre a verificação de cada produto e imprime só as falhas | Antes de dizer que acabaste |

**Carrega só o que o trabalho pede.** Não leias `products/` que não sejam o teu.
O `CLAUDE.md` do produto tem prioridade sobre este. Cada produto tem a sua toolchain
e o seu build; este ficheiro não assume nenhuma.

## Comandos (da raiz)

- `scripts/check` — verificação dos produtos alterados, output só com falhas. **Corre antes de dizer que acabaste.**
- `scripts/check <produto>` — um produto só; `scripts/check --all` — todos.

## Regras duras

1. Dados por definir ficam vazios (`null`) e a UI mostra marcador provisório. Nunca inventar valores.
2. Dependência nova: uma linha a justificar (porquê, licença, alternativa) no `SPEC.md` do produto.
3. Mínimo de dados pessoais. Cada dado recolhido tem finalidade escrita no `NOTES.md`.
4. Segredos nunca entram no repo. `.env.example` sim, `.env` não.
5. Ficheiros pequenos. Acima de ~300 linhas, dividir.
6. Texto visível passa por i18n quando o produto tem mais de uma língua.
7. <REGRA ESPECÍFICA DESTA ENTIDADE, se houver; apagar se não>

## Gates

O trabalho pára e espera por aprovação em quatro pontos: **o spec**, **o mockup**,
**a publicação** e **o texto final**. Quem aprova cada um está em
`handbook/03-ways-of-working.md`. Dentro de um spec aprovado o agente tem autonomia
total, incluindo testes e documentação, e só volta quando acaba ou está bloqueado.

## Disciplina de sessão

- Começar limpo: este ficheiro, depois só o que o trabalho nomeia.
- Terminar escrevendo o que mudou em `products/<nome>/NOTES.md` ou num ADR.
- Não colar logs de build no contexto. Correr `scripts/check` e ler só as falhas.
- Usar plan mode antes de cada fatia vertical.
- Este ficheiro não passa de 80 linhas. O que cresce vai para o handbook ou para um ADR.

## Língua

Documentação em <português de Portugal | inglês>. Código, identificadores e commits em inglês.

## Por fazer

- [ ] Preencher `handbook/00-manifesto.md`
- [ ] ADR-001: quem aprova cada gate, língua, stack inicial
- [ ] Primeiro produto em `products/<nome>/`
