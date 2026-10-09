# Visão

## O problema

Em casa, sozinho ou com amigos, o dono do projeto quer a TV (e mais tarde um projetor na parede) **apenas a dar imagem**: algo agradável de olhar e que **crie uma boa luz** na sala. Hoje faz isso com a TV sem som e a música a tocar do PC, mas sem uma imagem pensada para esse papel.

## O que queremos

Uma app que **produz imagem generativamente** para servir de ambiente e iluminação, com todas as opções escolhidas de forma independente:

| Eixo | Opções |
|---|---|
| **Reatividade** | nenhuma · áudio do PC (loopback) · telemóvel (microfone ou áudio do próprio telemóvel) · (na prateleira) contexto: hora do dia, meteorologia |
| **Fonte de imagem** | nenhuma (puramente sintetizado) · pasta local · acervos open access (museus, NASA) · outras fontes · (futuro) imagens geradas por IA no PC |
| **Estética / cena** | várias cenas para escolher, com parâmetros ajustáveis, playlists e transições |
| **Saída** | perfil TV · perfil Projetor (ajustável ao projetor concreto) |

Qualquer combinação destes eixos tem de funcionar.

## Princípios

1. **Luz primeiro, espetáculo depois.** O objetivo é ambiente. Movimento lento, gradientes ricos, cor bonita. A reatividade por defeito é **lenta** (segue a energia da música em janelas de 20–60 s); a reação ao beat é subtil ("respirar"), nunca estroboscópica. Há um controlo de intensidade de 0 a "festa".
2. **Nunca parado, nunca repetido.** Os parâmetros de cada cena vagueiam lentamente ("drift"), mesmo sem sinal nenhum.
3. **Pensado para projetor.** Projetores têm pretos fracos e perdem detalhe fino: preferir cenas saturadas, de luminosidade média, formas grandes e gradientes; o passe de saída tem dithering para evitar banding.
4. **A imagem é a iluminação da sala.** Controlo explícito do brilho geral e da temperatura de cor (ex.: mais quente à noite).
5. **Leve e autónomo.** Corre a noite inteira numa box modesta sem aquecer; funciona sem PC, sem telemóvel e sem internet.
6. **Controlo sem atrito.** Escolher cenas e ajustar parâmetros pelo telemóvel (página web aberta por QR code), não às voltas com o comando da TV.

## Fora de âmbito (por agora)

- Publicar na Play Store (sideload chega).
- Vídeo pré-gravado como fonte principal.
- Suporte a iOS na app emissora de sinais (a página de controlo web funciona em qualquer telemóvel).

## Ideias para mais tarde

Na prateleira (DABS, 2026-10-09, decisão 19): não são essenciais e não se fazem sem DABS as pedir.

- Contexto: hora do dia, nascer/pôr do sol e meteorologia a mudar a paleta ou a temperatura sozinhos.
- Ecrã do telemóvel como touchpad que injeta forças no flow field ou "pinta" luz; acelerómetro para agitar a cena.
- Imagens geradas por IA no PC (ex.: Stable Diffusion local), uma a cada poucos minutos, alimentando cenas híbridas.
- Ligar a luz da sala (lâmpadas inteligentes) à paleta atual.
- **Renderizar em qualquer dos três dispositivos.** Além da box, poder gerar a imagem no telemóvel ou no PC, ligados por cabo ou sem fios ao projetor/TV; ou, se o projetor vier com sistema de apps (Android TV ou semelhante), correr a app lá dentro, sem box. Os shaders já são portáteis (GLSL ES 3.00 = WebGL2); o que muda é o host. A decidir depois de comprar o projetor — ver `ARQUITETURA.md`, "Outros hosts".
