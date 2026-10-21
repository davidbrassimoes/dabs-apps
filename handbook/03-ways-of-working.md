# Ways of working

Uma ou mais pessoas, uma equipa de agentes, e uma regra que mantém tudo coerente:
**o repo decide.**

## Princípios de método

1. **Agentic first.** Se um agente não consegue navegar, alterar e verificar algo sozinho,
   a estrutura está errada, não o agente.
2. **Explícito em vez de mágico.** Cada convenção que o agente tem de descobrir custa
   tokens e causa erros.
3. **Chato e estável.** APIs maduras ganham a novidades. Desviar exige medição.
4. **Feedback rápido.** Um comando, segundos, output curto.
5. **Handoffs por ficheiros, nunca por conversa.** Quem implementa lê o `SPEC.md` numa
   sessão nova, sem memória de como foi escrito. É a maior poupança do setup e o que
   impede a deriva: um agente não herda um mal-entendido que nunca ouviu.
6. **Performance dos dois lados.** Software rápido para quem o usa; contexto pequeno para
   quem o constrói.

## Pipeline

```
brief → [researcher, se preciso] → [architect] SPEC.md
      → ██ GATE 1: spec ██
      → [design] mockup → ██ GATE 2: mockup ██
      → [dev] fatias verticais (plan mode, implementação, scripts/check)
      → [reviewer] revisão contra o spec e a barra
      → ██ GATE 3: publicação ██
      → [copy] texto final → ██ GATE 4: texto ██
```

## Os quatro gates

O trabalho pára e espera **exatamente** nestes pontos:

| Gate | Quando | Quem aprova | Registo |
|---|---|---|---|
| 1. Spec | Antes de existir código. O sítio mais barato para mudar de ideias. | DABS (ADR-001) | `SPEC.md`, "Estado: aprovado (data)" |
| 2. Mockup | Antes de a interface ser construída | DABS (ADR-001) | `NOTES.md` |
| 3. Publicação | Antes de algo sair para fora | DABS (ADR-001) | `RELEASE.md` |
| 4. Texto | Antes de texto público ir para fora | DABS (ADR-001) | `NOTES.md` |

Não são gates: fazer merge, gastar tempo de CI, escolher um nome de variável. Dentro de um
spec aprovado o agente tem **autonomia total**: decide o como, incluindo testes e
documentação, e só volta quando acaba ou está genuinamente bloqueado. É essa autonomia
que torna o gate do spec digno de existir.

## Os agentes (`.claude/agents/`)

| Agente | Faz | Não faz | Modelo |
|---|---|---|---|
| `researcher` | Dossier com fontes e grau de confiança antes de um spec: mercado, requisitos de lojas, licenças, prior art | Decidir | `opus` |
| `architect` | Brief → `SPEC.md` em fatias verticais; revê trabalho feito contra o spec | Escrever código de produto | `best` |
| `dev` | Implementa uma fatia aprovada, com testes, e deixa `scripts/check` verde | Decidir âmbito ou stack | `sonnet` |
| `reviewer` | Revê uma fatia contra o spec, a barra e as regras duras; relata, não corrige | Mudar código | `best` |
| `copy` | Texto público na voz do manifesto, honesto, com marcadores onde falta decisão | Decidir estrutura | `opus` |
| `design` | Tokens, componentes, acessibilidade, revisão visual | Escrever texto público | `best` |

**Escalões de modelo.** Todos os agentes e skills fixam `model:`; nenhum herda. Herdar fazia
o resultado depender do modelo com que a sessão abria, e uma sessão em Fable gastava Fable
em tudo o que herdava (o limite semanal esgotou assim em 2026-10). Os escalões:

- `best` (Fable onde a conta o tem, senão Opus): planeamento, design e o quality gate —
  `architect`, `reviewer`, `design`. Pouco volume, decisões que se propagam.
- `opus`: julgamento com volume — `researcher` (lê muitas páginas), `copy`.
- `sonnet`: implementação dentro de um spec aprovado (`dev`) e skills de leitura, git e docs.
- `haiku`: só skills mecânicas sem risco (correr uma ferramenta e reportar).

**Ressalvas do `best`.** Só cai para Opus quando a conta não tem Fable; com o limite do
Fable esgotado, o agente dá erro. Nesse caso chama-o com o modelo `opus` na invocação. A
documentação do Claude Code não lista `best` no frontmatter de agentes (testado e funciona,
2026-10-21); se deixar de funcionar, troca-se por `fable`.

**A sessão que conduz** abre em `opus` por defeito, `sonnet` para arrumação e git, Fable só
numa sessão em que se pensa diretamente com ele. **Sinais de que isto está errado:** voltar
a bater no limite do Fable com sessões em Opus → desce primeiro o `reviewer` para `opus`.

**Escrevem-se quando são precisos.** Um agente definido antes do primeiro trabalho real
está normalmente errado. Os cinco aqui são o mínimo que dois workspaces provaram; o `design`
só se escreve quando houver interface. Um agente novo (release, dados, hardware) nasce no
dia em que há um trabalho para ele, do que os anteriores ensinaram.

## Disciplina de contexto e custo

- **`CLAUDE.md` curto.** Carrega em todas as sessões; cada linha custa para sempre.
  Limite: 80 linhas. O que cresce vai para o handbook ou para um ADR.
- **O handbook está dividido** para que um trabalho leia só a sua secção.
- **Cada agente começa limpo** e lê só os seus inputs.
- **Hooks e `scripts/check` correm builds e testes** e dizem passou ou falhou. Um agente a
  ler logs é caro; um script a dizer "falhou, aqui está a falha" não é.
- **Toda a sessão acaba a escrever estado** em `products/<nome>/NOTES.md` ou num ADR. A
  próxima lê uma página em vez de repetir uma conversa.
- **Skills e referências carregam a pedido**, nunca por antecipação.

## Trabalhar a vários

Vale com uma pessoa; com duas ou mais deixa de ser boa prática e passa a ser o mecanismo.

- **O repo é a única memória partilhada.** A memória automática do Claude Code é por
  máquina: o que o agente de um aprende não chega ao do outro. `knowledge/`, ADRs e
  `NOTES.md` são a sincronização. "Não está commitado, não está decidido" vale a dobrar.
- **`main` protegida.** Trabalho em branches curtas, PR com CI verde e uma revisão
  (humana ou `/code-review`) antes do merge. Sem push direto a `main`.
- **Worktrees de agentes fora do histórico.** `.claude/worktrees/` está no `.gitignore`.
  Uma branch de agente que valha a pena guardar faz push como qualquer outra.
- **Configuração em duas camadas.** `.claude/settings.json`, agentes e skills são do repo
  e iguais para todos. `.claude/settings.local.json` e `~/.claude/CLAUDE.md` são de cada
  um e ficam fora. Segredos só em `.env` locais; `.env.example` no repo.
- **Cada produto tem um dono**, nomeado em `projects/<nome>.md`. É quem aprova os gates
  que o ADR-001 lhe atribui e quem resolve quando duas sessões se pisam.
- **Issues para bugs e pequenas adições**; direção estratégica e mudanças de âmbito vão
  para `projects/<nome>.md` e, se virarem uma regra, para um ADR.

## Onde se pensa

Iteração de marca, UI e texto acontece em **artefactos no chat**, onde se comparam opções
lado a lado e se deitam fora baratas. Só a versão aprovada é commitada. Depois disso vive
em código ou num ficheiro do produto, e o artefacto é história.

## Registo de decisões

Tudo o que muda uma regra do handbook ganha um ADR em `handbook/decisions/`, numerado,
datado, com o raciocínio e o que substitui. Nunca se muda uma regra em silêncio.
