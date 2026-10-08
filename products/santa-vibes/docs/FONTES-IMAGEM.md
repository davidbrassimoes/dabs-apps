# Fontes de imagem

> As APIs externas mudam. Antes de implementar cada fonte (Fase 2), **verificar a documentação atual** e anotar aqui a data da verificação.

## Princípios

- **Pedir no tamanho certo.** ~1280 px no lado maior chega (a cena corre a escala interna). Usar **IIIF** sempre que a fonte o suportar: o URL define o tamanho, por isso nunca se descarregam originais enormes.
- **Licenças**: preferir domínio público / CC0. Guardar sempre título, autor, instituição e licença junto da imagem na cache (para o overlay de crédito opcional).
- **Cache em disco LRU** com limite configurável (ex.: 300 MB) e prefetch em background. Offline, roda pelo que estiver em cache.
- **Filtros por fonte** expostos na página de controlo (ex.: só pinturas, só paisagens, cor dominante).
- Chaves de API nunca no repositório: ficheiro local ignorado pelo git (`local.properties` / `secrets.json`).

## Fontes candidatas

| Fonte | Acesso | Tamanho controlado | Notas | Verificado |
|---|---|---|---|---|
| **Pasta local** | ficheiros na box ou partilha do PC | redimensionar ao importar | as tuas fotos; zero dependências | — |
| **Servidor de casa** (feito 2.1, fonte `server`) | pasta `/mnt/drive2/santa-vibes/imagens` que DABS enche, servida pelo Apache em `http://192.168.1.200/santa-vibes/` (listagem de diretório); a box lê com `HttpURLConnection` | não: a box reduz ao descodificar (≤ 1024 px) | imagens pessoais de DABS; o que sai da pasta sai da cache da box na atualização seguinte | 2026-10-08 |
| **Art Institute of Chicago** | API pública, sem chave (a pesquisa funciona) | sim, IIIF | **bloqueado**: as imagens IIIF (`www.artic.edu/iiif/2`, o endereço que a própria API indica) respondem a clientes que não são browsers com um desafio Cloudflare (403, `cf-mitigated: challenge`); não se contorna. Fora das fontes; rever mais tarde | 2026-10-08 |
| **The Met** (feito 2.2, fonte `met`) | API pública, sem chave; `/v1/search` foi retirado a 2026-10-01, usa-se `/v1.1/search` (paginado) + `/v1/objects/{id}` | não: `primaryImageSmall` ("web-large", ~600 px) | o filtro de domínio público da pesquisa não chega: confirma-se `isPublicDomain` por objeto; temas rodam (paisagem, mar, céu, flores…) | 2026-10-08 |
| **Rijksmuseum** | dados abertos / IIIF | sim | a API mudou recentemente — confirmar o modelo atual | por verificar |
| **NASA Image Library** (feito 2.4, fonte `nasa`) | `images-api.nasa.gov`, **sem chave** (a APOD é que pede chave) | sim: `~medium.jpg` (~1280 px) | espaço, nebulosas, Terra; sem direitos de autor salvo indicação, crédito guardado | 2026-10-08 |
| **Wikimedia Commons** | API pública | sim (thumbnails por largura) | licenças variadas — filtrar | por verificar |
| **Unsplash** | API com chave | sim (parâmetros de URL) | regras de atribuição e de "hotlinking" a respeitar | por verificar |
| **IA no PC** (futuro) | servidor local | sim | ver Fase 4 | — |

## Extração de paleta

- Uma vez por imagem, ao carregar, numa thread de background.
- Reduzir a imagem a 64×64, **k-means em OKLab** para 5 cores (decidido 2026-10-08: determinístico, início nos quantis de luminosidade 10/30/50/70/90 %, 12 iterações), ordenar por luminância. `PaletteExtract.kt` na tv-app e `extract.ts` na autoria, verificados pela mesma fixture.
- Guardar a paleta nos metadados da cache para não recalcular.
