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
| **Art Institute of Chicago** | API pública, sem chave | sim, IIIF | filtro de domínio público; boa primeira fonte | por verificar |
| **The Met** | API pública, sem chave | não (imagem "small"/original) | filtro de domínio público | por verificar |
| **Rijksmuseum** | dados abertos / IIIF | sim | a API mudou recentemente — confirmar o modelo atual | por verificar |
| **NASA (APOD, Image Library)** | API com chave gratuita | parcial | espaço, nebulosas — ótimo para paletas | por verificar |
| **Wikimedia Commons** | API pública | sim (thumbnails por largura) | licenças variadas — filtrar | por verificar |
| **Unsplash** | API com chave | sim (parâmetros de URL) | regras de atribuição e de "hotlinking" a respeitar | por verificar |
| **IA no PC** (futuro) | servidor local | sim | ver Fase 4 | — |

## Extração de paleta

- Uma vez por imagem, ao carregar, numa thread de background.
- Reduzir a imagem (ex.: 64×64), k-means ou median cut para 5 cores, ordenar por luminância.
- Guardar a paleta nos metadados da cache para não recalcular.
