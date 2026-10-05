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
| tripTenthsKm | Int? | conta-km parcial lido ao abastecer, antes de o pôr a zeros (décimas de km): a distância do ciclo; null se não registado |
| volumeMl | Int | litros × 1000 (evitar floats) |
| pricePerLiterMilliCents | Int ou Long | preço/litro com 3 casas decimais (ex: 1,749 €) |
| totalCents | Long | total pago em cêntimos (= campo O do QR: total do documento, já com os descontos do talão) |
| discountCents | Long | descontos do talão em cêntimos (positivos, default 0); `litros × preço/litro = total + desconto` |
| isFullTank | Boolean | default true |
| stationNif | String? | do QR code |
| stationName | String? | local do posto em texto livre (ex.: "Loures B-Sentido N/S"); sugerido ao ler o talão: o já dado a esse NIF noutro abastecimento, senão o lido no texto |
| brand | FuelBrand? | marca, de uma lista fixa guardada por código estável (GALP, REPSOL, MOEVE…, OTHER); sugerida como o local. Não se deduz do NIF: o NIF do QR é o da empresa que explora o posto, não o da marca |
| fuelType | FuelType? | Gasolina 95, Gasolina 98, Gasóleo, GPL; lido do talão, senão o da última vez |
| fuelPremium | Boolean? | aditivado (ex.: "EFI 95 PREM", Ultimate, G-Force, V-Power); null se o talão não disser |
| atcud | String? | do QR code; **único** quando presente (evita duplicados) |
| buyerNifPresent | Boolean? | o QR indica se a fatura tem NIF do comprador (só registado; a app não guarda o NIF do utilizador nem avisa) |
| notes | String? | |
| createdAt | Instant | |

Valores monetários e volumes guardados como inteiros. Conversão e formatação só na UI (locale pt-PT: vírgula decimal, €).

## Cálculos
Para cada abastecimento cheio *i* com abastecimento cheio anterior *i-1*:
- `distância_i = odo_i − odo_{i−1}`, confrontada com o conta-km parcial quando registado (o utilizador põe-no a zeros a cada abastecimento): batem (±2 km) → conta-km; parcial menor → faltou registar um abastecimento → parcial; parcial maior → não foi posto a zeros → conta-km; ambos os casos ficam assinalados. Sem cheio anterior registado (o primeiro abastecimento) usa-se o parcial. O parcial só conta se o ciclo for um único abastecimento.
- `L/100km_i = litros_i / distância_i × 100`
- Se houver abastecimentos não cheios entre dois cheios, somar os litros de todos até ao cheio seguinte (suporte futuro).

Agregados por mês e por ano:
- km percorridos, litros, € gastos, média L/100 km (ponderada: litros totais / km totais × 100, **não** média das médias), €/km, preço médio por litro
- Autonomia: km habituais por depósito (média dos últimos ciclos). O utilizador atesta sempre logo depois da reserva, por isso não se usa a capacidade do depósito (que deixou de existir nas Definições)
- Deteção de anomalias: consumo de um ciclo muito acima da média móvel (ex: > 20%)

Toda a lógica de cálculo em funções puras, testadas unitariamente.

## Validações
- `odometerKm` > odómetro do abastecimento anterior (por data)
- Conta-km parcial ≈ odómetro − odómetro anterior (±2 km); aviso, não bloqueio
- Distância desde o último abastecimento plausível face aos litros (ex: consumo entre 2 e 25 L/100 km); aviso, não bloqueio
- `litros × preço/litro − desconto ≈ total` (tolerância de alguns cêntimos)
- ATCUD duplicado → aviso "este talão já foi registado"

## Leitura do talão

### QR code da AT (obrigatório em faturas portuguesas)
Formato: campos `CHAVE:valor` separados por `*`. Campos relevantes:
- `A` NIF do emitente (posto)
- `B` NIF do adquirente (`999999990` = consumidor final sem NIF)
- `F` data do documento (`AAAAMMDD`)
- `G` identificação do documento
- `H` ATCUD
- `O` total do documento com impostos (já descontados os descontos do talão)
- (outros: C, D, E, I*/J*/K*, N, Q, R; ignorar se não precisos)

Confirmar o formato contra a especificação técnica da AT para o código QR antes de implementar o parser e escrever testes com exemplos reais.

### OCR do talão (mesma imagem)
O QR não inclui litros nem preço/litro. Extrair por ML Kit Text Recognition com regras/regex tolerantes a variações dos talões portugueses (ex: `LITROS`, `L`, `QTD`, `€/L`, `PREÇO`; o OCR lê muitas vezes `€/L` como `e/L`). Extrair também os descontos (`DESCONTO`, `PROMO`, `CUPÃO`…; num desconto por litro conta o valor, não a taxa) e o total impresso. Usar o total do QR para validar: `litros × preço ≈ total + desconto`. Ignorar os quadros de impostos (ISP, IVA), que têm o mesmo padrão quantidade × taxa = valor. Sem QR legível, o total impresso substitui o do QR. Guardar amostras anonimizadas de talões para testes (`app/src/test/resources/receipts`, `real_*` = texto devolvido pelo ML Kit para fotografias reais).

Na câmara, o QR lê-se nos frames de análise; o texto lê-se numa fotografia em resolução máxima tirada logo a seguir (apagada depois). Para afinar os parsers com fotografias reais: pô-las em `test-photos/` (fora do git) e correr `SamplePhotoOcrDumpTest` com `adb shell am instrument`, que escreve o texto reconhecido em `Android/data/pt.dabs.fueltracker/files/ocr-dump/`. Em builds de debug, o formulário mostra a secção "Texto lido (debug)".

## Leitura do conta-km
- Conta-km digital (LCD, sete segmentos). No Corsa cada algarismo é feito de blocos quadrados numa grelha 5×7, com o parcial ("602,0") por cima do total e o rótulo "km" entre os dois.
- O ML Kit não serve: na foto inteira não lê nenhum dígito do visor e no recorte confunde 6 com 5. Usa-se um leitor próprio de sete segmentos (`SevenSegmentReader`, funções puras): binarização em vários limiares (Otsu e varredura), fusão dos blocos em algarismos, segunda segmentação dentro da faixa de cada linha (o "km" deixa de fazer ponte), leitura dos 7 segmentos, deteção da vírgula decimal (exclui o parcial) e votação entre limiares.
- Visor da câmara com moldura para enquadrar só os dígitos. Análise em tempo real (CameraX ImageAnalysis, plano Y, zona da moldura): fixar quando o mesmo número aparece estável em N frames consecutivos.
- Fotografia da galeria: o mesmo leitor na imagem inteira; o OCR genérico só como recurso para outros visores.
- O painel mostra sempre dois valores: o parcial em cima (uma casa decimal) e o total em baixo, que é o maior. O utilizador põe o parcial a zeros a cada abastecimento, por isso ao abastecer total ≈ odómetro anterior + parcial (±2 km): serve para escolher o total entre leituras duvidosas e é mostrado no ecrã de leitura (bate / não bate). Nunca bloqueia, porque o parcial pode não ter sido posto a zeros.
- Validar contra o último odómetro (uma leitura implausível é rejeitada, nunca substituída por outra).
- Fallback: teclado numérico com o valor previsto pré-preenchido.
- Amostras de teste em `app/src/test/resources/odometer` (foto real do painel e recorte do visor).

## Ecrãs
1. **Início**: último consumo, média recente, este mês (km, €, litros), autonomia estimada, botão grande "Abastecer".
2. **Novo abastecimento** (fluxo): scan talão → scan painel → ecrã de confirmação com todos os campos editáveis e avisos de validação → guardar.
3. **Histórico**: lista de abastecimentos com consumo de cada ciclo; editar e apagar (com undo).
4. **Estatísticas**: gráficos de L/100 km ao longo do tempo, € e km por mês, preço/litro; resumo por ano; por posto; por marca; por combustível (preço médio e consumo médio, atribuindo cada ciclo ao combustível do abastecimento que o começou, que é o que se queimou).
5. **Definições**: export/import CSV. (O NIF do utilizador e a capacidade do depósito foram retirados: o NIF não compensa no IRS quando as despesas gerais familiares já chegam ao limite, e a app não pode pô-lo numa fatura já emitida.)

## Fases
1. **Fundações**: dependências, arquitetura, Room, entrada manual, histórico, cálculos com testes, ecrã inicial.
2. **Scan do talão (QR)**: CameraX + Barcode Scanning, parser AT com testes, deteção de duplicados.
3. **OCR do talão**: litros e preço/litro, validação cruzada com o total.
4. **OCR do conta-km**: leitura em tempo real com estabilização.
5. **Estatísticas e polimento**: gráficos Vico, anomalias, export/import CSV, widget Glance.
