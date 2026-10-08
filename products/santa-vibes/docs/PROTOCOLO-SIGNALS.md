# Protocolo de sinais (v1)

Transporte: **UDP**, rede local. Todos os inteiros e floats em **little-endian**. Floats IEEE-754 de 32 bits.

## Portos

| Porto | Direção | Uso |
|---|---|---|
| 47800 | emissor → TV (unicast) | pacotes `SIGN` (~60 Hz) |
| 47801 | TV → broadcast | beacon `PNMB` (de 2 em 2 s) |

## Beacon `PNMB` (TV → rede) — 16 bytes

| Offset | Tipo | Campo | Notas |
|---|---|---|---|
| 0 | `u8[4]` | magic | ASCII `PNMB` |
| 4 | `u8` | version | `1` |
| 5 | `u8` | reserved | `0` |
| 6 | `u16` | signalPort | porto onde a TV escuta (`47800`) |
| 8 | `u16` | controlPort | porto HTTP do ControlServer (ex.: `8080`) |
| 10 | `u16` | reserved | `0` |
| 12 | `u32` | deviceId | identificador aleatório da TV, estável entre arranques |

O emissor usa o IP de origem do beacon como destino. Se receber beacons de várias TVs, envia para todas (ou para a escolhida na UI).

## Pacote `SIGN` (emissor → TV) — 56 bytes

| Offset | Tipo | Campo | Intervalo / notas |
|---|---|---|---|
| 0 | `u8[4]` | magic | ASCII `SIGN` |
| 4 | `u8` | version | `1` |
| 5 | `u8` | sourceKind | ver tabela abaixo |
| 6 | `u16` | flags | ver tabela abaixo |
| 8 | `u32` | sequence | incrementa por pacote; a TV descarta pacotes fora de ordem |
| 12 | `u32` | senderTimeMs | relógio monotónico do emissor (ms) |
| 16 | `f32` | energy | 0..1, rápido |
| 20 | `f32` | low | 0..1 |
| 24 | `f32` | mid | 0..1 |
| 28 | `f32` | high | 0..1 |
| 32 | `f32` | centroid | 0..1 (brilho espectral normalizado) |
| 36 | `f32` | beat | 0..1, envelope (1 no onset, decai) |
| 40 | `f32` | beatPhase | 0..1, fase prevista dentro do beat |
| 44 | `f32` | bpm | 0 = desconhecido |
| 48 | `f32` | moodEnergy | 0..1, janela 20–60 s |
| 52 | `f32` | moodBrightness | 0..1, janela 20–60 s |

### `sourceKind`

| Valor | Fonte |
|---|---|
| 0 | desconhecida |
| 1 | PC loopback |
| 2 | telemóvel — microfone |
| 3 | telemóvel — playback capture |
| 4 | reservado (era MIDI clock, retirado em 2026-10-07: `decisoes/0003-sem-midi.md`) |

### `flags` (bits)

| Bit | Significado |
|---|---|
| 0 | onset de beat neste pacote |
| 1 | `bpm`/`beatPhase` válidos |
| 2 | reservado (era "tempo bloqueado a MIDI clock"), enviado a `0` |
| 3 | silêncio (abaixo do limiar) |
| 4–15 | reservados (`0`) |

## Canal local para o authoring (1.6)

O `pc-bridge` também serve os mesmos pacotes `SIGN` num WebSocket local, `ws://127.0.0.1:47810`
(`--ws <porta>|off`): um frame binário de 56 bytes por pacote, ao mesmo ritmo do UDP, só para
páginas servidas deste PC (cabeçalho `Origin` local). Não é um canal para a TV, que continua só
em UDP; serve para afinar cenas reativas no browser.

## Regras

- O emissor envia a ~60 Hz enquanto houver captura ativa; em silêncio pode baixar para 10 Hz com o bit 3 ligado.
- A TV considera a fonte **ausente** se não chegar nenhum pacote durante 1,5 s e faz transição suave para o `Synth`.
- Todos os valores já vêm **normalizados** pelo emissor (AGC no `signals-core`). A TV não faz análise de áudio.
- A TV suaviza os valores recebidos (interpolação até ao próximo pacote) para esconder jitter da rede.
- Pacotes com `magic`/`version` desconhecidos são ignorados.

## Versionamento

Qualquer alteração ao layout incrementa `version` e atualiza este documento. A TV deve aceitar pelo menos a versão atual e a anterior durante uma transição.
