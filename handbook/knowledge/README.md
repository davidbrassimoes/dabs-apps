# Knowledge base

O que a entidade aprendeu e que **generaliza entre produtos**, guardado onde o próximo
produto o lê de uma vez. **Um ficheiro por tópico, nunca um ficheiro que cresce.**

## O que é, e o que não é

- **É** a parte das descobertas de um produto que serve a outro: um mecanismo, uma
  medição e o seu método, uma armadilha de uma biblioteca ou plataforma, uma decisão que o
  utilizador ou o ouvido tomaram e o que os números disseram. Cada entrada diz quão certa
  é e de onde veio.
- **Não é** um spec, um dossier nem um registo de decisões. O raciocínio completo de um
  produto fica na pasta dele; aqui fica a descoberta destilada e um ponteiro. Nada aqui
  obriga um produto: o `SPEC.md` obriga. Onde um tópico e um spec discordam, o spec é a
  decisão desse produto, e o tópico ganha uma nota datada a dizê-lo.

## Quem lê e escreve

`researcher` lê os tópicos relevantes antes de um dossier e acrescenta o que aprendeu
depois. `architect`, `dev` e `reviewer` podem ler tudo: não há código aqui, só palavras,
números e diagramas. Quem fecha uma fatia que ensinou algo acrescenta-o.

## Etiquetas

| Etiqueta | Significado |
|---|---|
| **medido** | Medição nossa, com o quê e quando: "medido (produto X, build Y, AAAA-MM-DD)". É um facto sobre essa medição, não sobre o mundo. |
| **documentado** | Afirmado numa fonte publicada credível, não verificado por nós |
| **relatado** | Consenso de fóruns ou de quem tem o hardware ou a plataforma |
| **inferido** | Raciocínio nosso a partir do resto; claramente nosso |
| **decidido** | O que um produto escolheu e porquê, com ficheiro e data |

Nunca apresentar *inferido* como *documentado*. Cada número aponta para uma fonte ou para
um ficheiro e data. Onde não há número, escreve-se "lacuna" e o que a lacuna custa.

## Licenças

Quem escreve código não abre código copyleft (GPL, AGPL, LGPL) nem código sem licença.
Se o `researcher` o ler, descreve-o aqui em palavras, com o ficheiro e a licença citados
e a entrada marcada *lido*. Nenhum código copyleft, nem paráfrase próxima, é colado nesta
pasta. Código permissivo (MIT, BSD, ISC, Apache-2.0) pode ser recomendado; se um produto o
usa, passa a dependência registada no `SPEC.md`.

## Como acrescentar

1. Ler o ficheiro do tópico primeiro. Se a descoberta pertence a um tópico existente,
   acrescenta-se lá sob o título certo; se contradiz uma entrada, ficam as duas, datadas e
   etiquetadas, com indicação de qual o produto seguiu. Nunca se apaga em silêncio.
2. Se nenhum tópico serve, cria-se um ficheiro e uma linha no índice. Divide-se um ficheiro
   quando deixa de se ler de uma vez.
3. Atualiza-se a linha *Atualizado* do ficheiro e nomeia-se o produto e os ficheiros de
   origem.
4. Cada tópico tem a mesma forma: **o que sabemos** (etiquetado), **o que um produto
   decidiu e porquê**, **perguntas abertas**, **fontes**.

## Índice

| Ficheiro | Tópico | Ler quando |
|---|---|---|
| `ci-minutos-github.md` | Minutos do GitHub Actions em repos privados e o padrão de CI que os poupa (serve também ps2-ports, webdabs e digital-obelisk) | Ao mexer num `ci.yml` |
