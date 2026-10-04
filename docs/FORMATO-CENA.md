# Formato de cena (v1)

Uma cena vive em `scenes/<id>/` e tem dois ficheiros:

```
scenes/<id>/
  scene.frag   corpo do fragment shader (GLSL ES 3.00, sem #version)
  scene.json   metadados e esquema de parâmetros
```

`<id>` é kebab-case e igual ao campo `id` do JSON.

## Composição do shader

O host (tv-app e authoring, com o mesmo algoritmo) monta o shader final assim:

1. `shared/glsl/header.glsl` — `#version 300 es`, precisão, uniforms do contrato, `out vec4 fragColor;`
2. Uma linha por parâmetro declarado: `uniform float p_<id>;` (ou `vec3` para `color`)
3. O corpo de `scene.frag`, com as diretivas `#include "<caminho>"` substituídas pelo conteúdo de `shared/glsl/<caminho>` (cada ficheiro incluído no máximo uma vez).

`scene.frag` define `void main()` e escreve em `fragColor` (RGB linear-ish, 0..1; alfa ignorado).

## Uniforms do contrato

Definidos em `shared/glsl/header.glsl`. As cenas **só** podem depender destes e dos seus parâmetros.

| Uniform | Tipo | Significado |
|---|---|---|
| `uTime` | `float` | segundos desde o início da cena |
| `uResolution` | `vec2` | resolução do alvo de render da cena (já com `renderScale`) |
| `uBands` | `vec4` | `low`, `mid`, `high`, `energy` (rápidos) |
| `uMood` | `vec4` | `moodEnergy`, `moodBrightness`, `centroid`, `reservado` |
| `uBeat` | `vec4` | `beat` (envelope), `beatPhase`, `bpm`, `onset` (1 no frame do onset) |
| `uPalette[5]` | `vec3[5]` | paleta atual (da imagem, de um preset ou da cena) |
| `uImage` | `sampler2D` | imagem atual (se `image` ≠ `none`) |
| `uImageInfo` | `vec4` | `hasImage` (0/1), `aspect`, `mix de transição` (0..1), `reservado` |
| `uPrev` | `sampler2D` | frame anterior da própria cena (se `feedback: true`) |

Os sinais **já vêm misturados** com o sintético segundo a reatividade escolhida — a cena não precisa de saber se há fonte ativa. Com reatividade 0, os sinais continuam a mexer suavemente (LFOs).

## `scene.json`

```json
{
  "format": 1,
  "id": "nebula",
  "name": "Nebulosa",
  "description": "Domain warping de ruído; cores da paleta.",
  "tags": ["orgânico", "lento"],
  "fps": 30,
  "renderScale": 0.5,
  "feedback": false,
  "image": "optional",
  "defaultPalette": ["#0b1026", "#2a1b5e", "#7a3b8f", "#e07a5f", "#f2cc8f"],
  "params": [
    {
      "id": "speed",
      "label": "Velocidade",
      "type": "float",
      "min": 0.0,
      "max": 1.0,
      "default": 0.25,
      "drift": 0.15
    }
  ]
}
```

| Campo | Obrigatório | Descrição |
|---|---|---|
| `format` | sim | versão deste formato (`1`) |
| `id`, `name` | sim | identificador e nome visível |
| `description`, `tags` | não | para a página de controlo e playlists |
| `fps` | não | limite de fps desta cena (defeito: o do perfil) |
| `renderScale` | não | escala interna sugerida (defeito: a do perfil) |
| `feedback` | não | `true` → o host fornece `uPrev` (ping-pong) |
| `image` | não | `none` (defeito), `optional`, `required` |
| `defaultPalette` | não | 5 cores hex usadas quando não há imagem nem preset |
| `params` | sim | lista de parâmetros (pode ser vazia) |

### Parâmetros

| Campo | Descrição |
|---|---|
| `id` | nome; vira o uniform `p_<id>` |
| `label` | texto na UI |
| `type` | `float`, `int` (enviado como float), `bool` (0/1), `color` (`vec3`, valor em hex) |
| `min`, `max`, `default` | intervalo e valor por defeito (`color`: só `default`) |
| `step` | opcional; granularidade na UI |
| `drift` | 0..1 — amplitude do vaguear lento, como fração do intervalo (0 = fixo). Não se aplica a `bool`/`color`. |
| `group` | opcional; agrupa sliders na UI |

## Boas práticas

- **Pensar no projetor**: luminosidade média, cor saturada, formas grandes; evitar depender de pretos profundos e de detalhe fino.
- **Reatividade lenta por defeito**: usar `uMood` para mudanças de carácter e `uBeat.x` com moderação (respirar, não piscar). Expor um parâmetro tipo `pulse` para o utilizador dosear.
- **Custo**: preferir poucas oitavas de ruído; evitar loops longos por pixel; cenas `feedback` funcionam bem a `renderScale` baixa.
- **Sem banding**: o passe de saída faz dithering; não é preciso repetir na cena.
- Nada de estado global fora de `uPrev`; a cena deve poder recomeçar a qualquer momento.
