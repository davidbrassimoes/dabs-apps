# Spec — <nome>

**Estado:** rascunho | aprovado (AAAA-MM-DD, por <quem>)
**Brief:** `products/<nome>/BRIEF.md`
**Dono:** <quem> (`projects/<nome>.md`)

## 1. Âmbito
O que entra, o que não entra. Plataformas e versões mínimas.

## 2. Fatias verticais
Ordem de entrega. Cada fatia: objetivo, o que o utilizador consegue fazer no fim,
critério de aceitação **verificável** (um teste, um comando, uma medição).

| # | Fatia | Aceitação |
|---|---|---|
| 0 | Esqueleto: build, `scripts/check`, CI verde, ecrã vazio a arrancar | `scripts/check <nome>` verde na CI |

## 3. Modelo de dados
Entidades e relações. Quem pode ver e editar cada uma. Onde ficam (local, servidor).

## 4. Interfaces
Ecrãs, rotas, endpoints, protocolos, formatos de ficheiro. Com o schema de entrada e saída
onde se aplique. Contratos versionados dizem aqui a versão.

## 5. Permissões
Papéis e o que cada um faz. Permissões do sistema pedidas e porquê.

## 6. Performance
Orçamentos concretos para o que importa neste produto, e como se medem:
arranque, frame time, memória, tamanho do binário, LCP, latência. Cada um com o teste ou
comando que falha quando é ultrapassado.

## 7. Acessibilidade e i18n
Nível exigido. Línguas. Como se verifica.

## 8. Dados pessoais
Campo, finalidade, retenção, onde fica. Terceiros que os recebem, se algum.

## 9. Dependências e licenças
Cada dependência relevante: porquê, licença, alternativa considerada. Obrigações que a
licença cria (atribuição, fonte correspondente).

## 10. Testes
O que tem teste unitário, o que tem teste de integração ou e2e, o que se verifica à mão
em hardware e como.

## 11. Decisões tomadas neste spec
Numeradas, datadas, com quem decidiu. Uma decisão que mude uma regra do handbook vai para
um ADR em vez de ficar aqui.

## 12. Por decidir
O que o spec deixa em aberto, quem decide, e o que fica bloqueado até lá.
