# Registo de obrigações

> Copiado para `handbook/obrigacoes.md` quando o primeiro produto tiver destino fora do
> pessoal. Cada dever legal que a entidade assumiu, o que o cumpre, e se está cumprido.
> **Esta é a lista toda.** Um dever que não está aqui não está a ser seguido; um que está
> aqui por cumprir bloqueia o que diz que bloqueia. É um registo, não um processo: uma
> página que se lê em dois minutos ganha a uma revisão que tem de ser corrida.

## Marcas e nomes

| Dever | Estado | Onde vive |
|---|---|---|
| Nome da entidade verificado quanto a marcas, nas classes relevantes | por fazer | ADR |
| Cada nome de produto verificado antes de sair | por fazer | `products/<nome>/RELEASE.md` |
| Nenhuma marca de terceiros em nome, ícone, listagem ou capturas | por fazer | `RELEASE.md` |
| Domínios registados e renovação automática | | |

## Frameworks, plataformas e lojas

| Componente | Termos | O que devemos |
|---|---|---|
| <ex.: conta de developer da loja> | <titular, taxa, verificação> | <manter dados atuais, 2FA> |
| <ex.: SDK ou framework com licença por receita> | <limiar> | <vigiar o número> |

## Dependências por produto

Não se listam aqui, de propósito: uma cópia de uma lista é uma lista que fica errada. Cada
produto regista as suas no `SPEC.md` §9. Ficam aqui só as obrigações afiadas o suficiente
para que falhar seja violação de licença e não página desarrumada:

- <ex.: biblioteca X é LGPL e redistribuída: fonte correspondente publicada no mesmo passo>
- <ex.: biblioteca Y passa a termos comerciais a partir da versão Z: o Dependabot não a propõe>

## Dados pessoais

| Dever | Estado | Onde vive |
|---|---|---|
| Política de privacidade por produto que recolhe algo | | |
| Formulários de dados da loja coerentes com o `NOTES.md` | | `RELEASE.md` |
| Base legal e retenção escritas para cada dado | | `products/<nome>/NOTES.md` |
| Contacto para pedidos de titulares | | |

## Entidade e impostos

| Dever | Estado | Onde vive |
|---|---|---|
| Em nome de quem se vende (pessoa, empresa) e o que isso obriga | por decidir | ADR-001 |
| Quem é o merchant of record (loja, plataforma de pagamento) | | |
| Faturação e IVA no canal escolhido | | |
| Acordo escrito entre sócios sobre propriedade do código, se houver mais de um | por decidir | fora do repo; referido no ADR-001 |
