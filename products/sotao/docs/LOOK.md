# Aspeto — G · Baralho suave

**Estado:** aprovado por DABS no gate 2, 2026-10-06 ("ficamos com a G por enquanto").
Substitui o §12 de `IMPLEMENTATION.md` e o sea fog provisório. Referência visual:
`docs/look-2026-10-06/G-draw.dc.html` e `G-spread.dc.html` (canvas no `NOTES.md`). Os
ecrãs que o mockup não mostra (cartas, editor, material, importar, login, definições)
seguem estas regras; `C-cards.dc.html` é a referência de layout da lista de cartas, com as
cores daqui.

## Ideia

A estrutura da C (cartas de cor chapada, cantos redondos, letra grossa) com cores baixas e o
fundo castanho quente da A. A carta é a coisa mais clara do ecrã. Lê-se do outro lado de uma
sala escura com um instrumento ao colo.

## Tipos de letra

Os dois de `@fontsource`, servidos pela app (sem pedidos a terceiros), OFL 1.1.

| Papel | Fonte | Pesos |
|---|---|---|
| Texto das cartas, títulos, botões, interface | Bricolage Grotesque | 400, 600, 700, 800 |
| Rótulos (tipo de carta em maiúsculas, `letter-spacing: 0.2em`), slugs de material, contagens, marcador offline | Courier Prime | 400, 700 |

Nome da app em minúsculas, "sótão", Bricolage 800, `letter-spacing: -0.02em`.
Texto da carta: 700, `letter-spacing: -0.015em`. Números em colunas: `tabular-nums`.

## Cores

Tokens em `src/routes/layout.css`, os temas redefinem tokens e nunca componentes. Valores de
partida; medir e ajustar só a luminosidade até cumprir 4.5:1 (texto) e 3:1 (gráficos),
e escrever os valores medidos em `NOTES.md`, "Paleta".

| Token | Escuro (defeito) | Claro |
|---|---|---|
| `--ground` | `#17130F` | `#EFE9DE` |
| `--panel` | `#26201A` | `#E1D9CA` |
| `--line` | `#3A3229` | `#CBBFAC` |
| `--text` | `#F1EADD` | `#1C1814` |
| `--text-dim` | `#B3A897` | `#5B5146` |
| `--lamp` (botão Draw, foco, seleção) | `#E3A23F` | `#8F5410` |
| `--lamp-ink` | `#1B1307` | `#FFFFFF` |

**Cartas** — o fundo da carta é a cor do tipo, com tinta `#1C1814`, igual nos dois temas.
Exceção: a carta **ideia** no tema claro inverte (fundo `#1C1814`, tinta `#F1EADD`), porque
osso sobre fundo claro não se separa.

| Tipo | Fundo da carta | Como texto sobre `--ground` claro |
|---|---|---|
| idea | `#E9DFC9` | `--text` |
| source | `#8DB3A4` | `#3E6B5D` |
| treatment | `#DDAE63` | `#855A12` |
| technique | `#A9B2D6` | `#4A5590` |
| constraint | `#D08A74` | `#9A4430` |
| arrangement | `#BFA2C9` | `#6E4C7C` |

No tema escuro, o tipo como texto ou ponto usa a própria cor de fundo da carta.

## Formas e medidas

- Cartas e botões grandes: raio 18 px. Comutadores e chips: 999 px.
- Botão Draw: cor `--lamp`, 76 px de altura, Bricolage 800 a 28–30 px. Com cartas trancadas
  diz "Draw two" / "Draw one" (quantas vão sair).
- Alvos de toque ≥ 44 px. Foco visível em `--lamp`, 2 px, em todos os controlos.
- Sem sombras nas cartas; a separação é a cor.

## Layout

- **Telemóvel (até 720 px):** cabeçalho com "sótão" e, no Draw, o comutador One/Three em
  pílula; uma linha em Courier com o chip do rack ("18 of 42 in the rack", liga a `/gear`) e
  "3 / 7 tonight" ou "Offline · cached"; a carta ocupa o resto; por baixo, o botão anterior
  (64×76) e o Draw; **navegação em barra no fundo** (Draw, Cards, Gear, Import), a secção
  atual em 800, as outras em 600 `--text-dim`. Definições a partir de um botão no cabeçalho.
- **Largo (> 720 px):** a navegação sobe para o cabeçalho; o conteúdo fica centrado com
  largura máxima (Draw ~560 px, listas ~960 px).
- No modo Three, as três cartas empilham-se e repartem a altura; cada uma tem o tipo em
  Courier e o botão Keep/Kept (pílula com contorno; trancada fica preenchida a `#1C1814`).

## Temas

Três estados: Auto, Dark, Light. Auto segue o sistema e cai para **Dark** quando o sistema
não diz (o Draw é usado numa sala escura). Valores guardados antigos mapeiam:
`night`/`dark` → `dark`, `morning`/`light` → `light`, `system` → `auto`.

## Ícones

`scripts/icons.mjs` redesenha os ícones com esta paleta: fundo `--ground` escuro, uma carta
`#E9DFC9` inclinada com uma barra `--lamp`.
