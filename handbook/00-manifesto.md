# Manifesto

> **Por preencher.** Este ficheiro é o conjunto de perguntas que definem a entidade. Os
> agentes leem-no antes de um brief ou spec, por isso o que aqui estiver errado propaga-se
> a tudo. O que ainda não se sabe fica escrito como **por decidir**, nunca inventado.
> Quando estiver preenchido, apagar este bloco e registar a data no ADR-001.

## O que fazemos

Uma a três frases. Que tipo de software, para que uso.

## Para quem

Quem usa o que fazemos. Quem paga, se alguém paga. Que língua, que dispositivos, que
contexto.

## O que recusamos

Tão importante como o resto. Tipos de produto, de cliente, de prática ou de dependência
que não aceitamos, e porquê. Um agente que encontre um destes pára.

## Como decidimos

| Decisão | Quem | Onde fica |
|---|---|---|
| Spec aprovado | <nome ou "dono do produto" ou "ambos"> | `products/<nome>/SPEC.md`, "Estado" |
| Mockup aprovado | | `products/<nome>/NOTES.md` |
| Publicação | | `products/<nome>/RELEASE.md` |
| Texto final | | `products/<nome>/NOTES.md` |
| Regra do handbook muda | | ADR |
| Stack de um produto | | ADR |

Com mais de uma pessoa: o que acontece quando não há acordo.

## Como partilhamos (se houver mais de uma pessoa)

Quem detém o código. Como se divide o que render. Que acordo escrito existe e onde está.
**Isto resolve-se antes do primeiro produto sair**, porque muda a licença do repo e o que
cada um pode fazer com ele.

## Princípios

Três a sete. Cada um tem de ser capaz de recusar algo concreto; um princípio que nunca
diz não a nada não é um princípio. Os princípios de método (como trabalhamos com agentes)
já estão em `03-ways-of-working.md`; aqui ficam só os da entidade.

1.
2.
3.
