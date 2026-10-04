# Fuel Tracker — Especificação

App Android pessoal para registar abastecimentos e leituras do conta-km e calcular estatísticas de consumo e custos. O carro não tem computador de bordo: só tem o ponteiro do depósito e o conta-km digital (total + parcial).

## Princípios
- **Input o mais rápido possível**: abastecimento registado em ~10 segundos (scan do talão, scan do painel, confirmar).
- **Offline e local**: sem servidor, sem contas, sem permissão de Internet.
- **Fiável**: valores lidos automaticamente são sempre propostos e o utilizador confirma; validações de plausibilidade.
- **Bem construída**: código idiomático, testado, com boa performance.

## Stack
- Kotlin, Jetpack Compose, Material 3 (dynamic color, light/dark)
- Arquitetura MVVM + camada de domínio (use cases) quando fizer sentido; Hilt; Coroutines/Flow
- Room (SQLite) com migrações explícitas
- CameraX + ML Kit Barcode Scanning + ML Kit Text Recognition (on-device, modelos empacotados, sem dependência do Play Services download quando possível)
- Vico para gráficos
- Glance para o widget (fase posterior)
- Navigation Compose (type-safe routes)
- Testes: JUnit + Turbine para Flows; testes de instrumentação para Room

## Modelo de dados

O utilizador **atesta sempre**, por isso cada abastecimento fecha um ciclo cheio-a-cheio. Mesmo assim, o modelo deve suportar um flag `isFullTank` (por defeito `true`) para não fechar portas no futuro.

### FillUp
| Campo | Tipo | Notas |
|---|---|---|
| id | Long | PK |
| dateTime | Instant/LocalDateTime | data do talão; hora se disponível |
| odometerKm | Int | total do conta-km no momento do abastecimento |
| volumeMl | Int | litros × 1000 (evitar floats) |
| pricePerLiterMilliCents | Int ou Long | preço/litro com 3 casas decimais (ex: 1,749 €) |
| totalCents | Long | total pago em cêntimos |
| isFullTank | Boolean | default true |
| stationNif | String? | do QR code |
| stationName | String? | opcional, editável |
| atcud | String? | do QR code; **único** quando presente (evita duplicados) |
| buyerNifPresent | Boolean? | o QR indica se a fatura tem NIF do comprador |
| notes | String? | |
| createdAt | Instant | |

Valores monetários e volumes guardados como inteiros. Conversão e formatação só na UI (locale pt-PT: vírgula decimal, €).

### Settings (DataStore)
- NIF do utilizador (para mostrar no ecrã "Mostrar NIF" e comparar com o do QR)
- Capacidade do depósito (opcional, para validação e estimativas)

## Cálculos
Para cada abastecimento cheio *i* com abastecimento cheio anterior *i-1*:
- `distância_i = odo_i − odo_{i−1}`
- `L/100km_i = litros_i / distância_i × 100`
- Se houver abastecimentos não cheios entre dois cheios, somar os litros de todos até ao cheio seguinte (suporte futuro).

Agregados por mês e por ano:
- km percorridos, litros, € gastos, média L/100 km (ponderada: litros totais / km totais × 100, **não** média das médias), €/km, preço médio por litro
- Autonomia estimada desde o último atestar (usando a média recente)
- Deteção de anomalias: consumo de um ciclo muito acima da média móvel (ex: > 20%)

Toda a lógica de cálculo em funções puras, testadas unitariamente.

## Validações
- `odometerKm` > odómetro do abastecimento anterior (por data)
- Distância desde o último abastecimento plausível face aos litros (ex: consumo entre 2 e 25 L/100 km); aviso, não bloqueio
- `litros × preço/litro ≈ total` (tolerância de alguns cêntimos)
- Litros ≤ capacidade do depósito (se configurada)
- ATCUD duplicado → aviso "este talão já foi registado"

## Leitura do talão

### QR code da AT (obrigatório em faturas portuguesas)
Formato: campos `CHAVE:valor` separados por `*`. Campos relevantes:
- `A` NIF do emitente (posto)
- `B` NIF do adquirente (`999999990` = consumidor final sem NIF)
- `F` data do documento (`AAAAMMDD`)
- `G` identificação do documento
- `H` ATCUD
- `O` total do documento com impostos
- (outros: C, D, E, I*/J*/K*, N, Q, R; ignorar se não precisos)

Confirmar o formato contra a especificação técnica da AT para o código QR antes de implementar o parser e escrever testes com exemplos reais.

### OCR do talão (mesma imagem)
O QR não inclui litros nem preço/litro. Extrair por ML Kit Text Recognition com regras/regex tolerantes a variações dos talões portugueses (ex: `LITROS`, `L`, `QTD`, `€/L`, `PREÇO`). Usar o total do QR para validar: `litros × preço ≈ total`. Guardar amostras anonimizadas de talões para testes.

## Leitura do conta-km
- Conta-km digital (LCD, sete segmentos).
- Visor da câmara com moldura para enquadrar só os dígitos.
- Análise em tempo real (CameraX ImageAnalysis + ML Kit): fixar quando o mesmo número aparece estável em N frames consecutivos.
- Filtrar apenas dígitos; validar contra o último odómetro.
- Fallback: teclado numérico com o valor previsto pré-preenchido.
- Se o OCR genérico se revelar insuficiente para sete segmentos, considerar um modelo dedicado (fase posterior, só se necessário).

## Ecrãs
1. **Início**: último consumo, média recente, este mês (km, €, litros), autonomia estimada, botão grande "Abastecer".
2. **Novo abastecimento** (fluxo): scan talão → scan painel → ecrã de confirmação com todos os campos editáveis e avisos de validação → guardar.
3. **Histórico**: lista de abastecimentos com consumo de cada ciclo; editar e apagar (com undo).
4. **Estatísticas**: gráficos de L/100 km ao longo do tempo, € e km por mês, preço/litro; resumo por ano; por posto.
5. **Mostrar NIF**: ecrã de alto contraste com o NIF em grande, para mostrar ao funcionário.
6. **Definições**: NIF, capacidade do depósito, export/import CSV, tema.

## Fases
1. **Fundações**: dependências, arquitetura, Room, entrada manual, histórico, cálculos com testes, ecrã inicial.
2. **Scan do talão (QR)**: CameraX + Barcode Scanning, parser AT com testes, deteção de duplicados, aviso de NIF em falta.
3. **OCR do talão**: litros e preço/litro, validação cruzada com o total.
4. **OCR do conta-km**: leitura em tempo real com estabilização.
5. **Estatísticas e polimento**: gráficos Vico, anomalias, export/import CSV, widget Glance, ecrã "Mostrar NIF".
