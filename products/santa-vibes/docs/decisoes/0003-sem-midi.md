# 0003 — Sem MIDI clock

- **Estado:** aceite
- **Data:** 2026-10-07
- **Decide:** DABS
- **Substitui:** a parte `midir` do ADR 0001

## Contexto

O MIDI clock estava na visão desde o início (eixo Reatividade) e no roadmap como passo 1.5, sem
razão escrita. Ao chegar ao 1.5, DABS não se lembrava de o ter pedido. O uso real é música a tocar
do PC; o loopback já lê o tempo com cerca de 1 BPM de erro (1.2–1.4). O MIDI só ajudaria no
estúdio, com um DAW a dar tempo e fase exatos.

## Decisão

O MIDI clock sai do produto: da visão, da arquitetura, do roadmap e do código. O `pc-bridge` não
usa `midir`. No protocolo, o layout não muda: o `sourceKind` 4 e o bit 2 das flags ficam
reservados (enviados a `0`), por isso a `version` também não muda.

## Consequências

- O 1.5 passa a ser só a afinação da latência.
- A TV deixa de ter o modo de fonte `midi`; um pacote com `sourceKind` 4 conta como fonte desconhecida.
- Voltar a ter MIDI pede um ADR novo que diga para quê (por exemplo, o estúdio com um DAW).
