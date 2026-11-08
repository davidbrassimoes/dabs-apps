# Decisões (ADRs)

Um ficheiro por decisão, `ADR-NNN-titulo-curto.md`, numerado por ordem de criação, nunca
renumerado. Um ADR não se edita depois de aceite: substitui-se por outro que o cite.

Escreve-se um ADR quando:

- uma regra do handbook muda ou nasce;
- um produto escolhe ou muda de stack;
- algo que parece arbitrário precisa de ficar explicado para a próxima sessão;
- uma decisão tomada em conversa tem de passar a valer (se não está aqui, não está decidida).

Não se escreve um ADR para: escolhas dentro de um spec aprovado (vão para o `NOTES.md` do
produto), bugs, ou o estado de um projeto (`projects/<nome>.md`).

Modelo: `ADR-000-template.md`. O primeiro ADR de qualquer workspace é o das decisões
fundadoras: quem aprova cada gate, língua, stack inicial, forma de trabalhar a vários.

## Índice

| ADR | Título | Estado | Data |
|---|---|---|---|
| 001 | Decisões fundadoras do workspace | aceite | 2026-10-05 |
| 002 | Retirado (2026-11-08): saiu do repo com o produto a que se aplicava | retirado | 2026-11-08 |
| 003 | O repo é público | aceite | 2026-11-08 |
