# Release — <produto> v<x.y.z>

> Copiado de `templates/release-checklist.md` para `products/<nome>/RELEASE.md`. Nada é
> publicado com uma caixa por marcar. Gate 3: <quem> aprova a publicação. Gate 4: <quem>
> aprova o texto. Riscar o que não se aplica a este produto, com uma palavra a dizer porquê.

## Regras

- [ ] Cada regra dura do `CLAUDE.md` da raiz e do produto verificada, não assumida
- [ ] Cada critério de aceitação do `SPEC.md` passa
- [ ] Nenhum valor inventado em UI, configuração ou dados de exemplo visíveis

## Legal e obrigações

> O registo é `handbook/obrigacoes.md` (de `templates/obrigacoes.md`). Estas são as linhas
> que vencem numa release.

- [ ] **O nome que sai está verificado** quanto a marcas e a produtos existentes, e é o
      definitivo, não o provisório
- [ ] Identificadores permanentes fixados de propósito (`applicationId`, bundle id, nome
      de pacote, domínio): depois de publicar não mudam
- [ ] Cada dependência registada com a licença **na versão que sai**, não herdada da anterior
- [ ] Textos de atribuição e licença instalados com o binário, não só prometidos
- [ ] Componentes LGPL redistribuídos: fonte correspondente publicada no **mesmo passo**
- [ ] Nenhuma fonte GPL incluída em código distribuído
- [ ] Política de privacidade publicada e a dizer a verdade sobre o que a app faz
- [ ] Formulários de dados da loja (Data safety, App Privacy) coerentes com o `NOTES.md`
- [ ] Imagens, sons e fontes de terceiros com licença registada e créditos onde a licença
      os exige
- [ ] Nenhuma marca registada de terceiros em nome, ícone, listagem ou capturas

## Testes

- [ ] `scripts/check <nome>` verde na CI, no commit etiquetado
- [ ] Testes de integração ou e2e verdes
- [ ] Orçamentos de performance do `SPEC.md` §6 medidos nesta build e dentro do limite
- [ ] Testado à mão no hardware real listado no `SPEC.md` §10, com os números no `NOTES.md`
- [ ] Instalação limpa numa máquina ou dispositivo que nunca teve o produto
- [ ] Atualização sobre a versão anterior: dados e configuração preservados
- [ ] Desinstalação

## Build e assinatura

- [ ] Versão incrementada (semver), por produto
- [ ] Changelog em palavras simples; qualquer mudança de comportamento dita explicitamente
- [ ] Build feita de um checkout limpo de um commit etiquetado, não de uma árvore de trabalho
- [ ] Assinada com a chave de release; **a chave está fora do repo e tem backup fora deste disco**
- [ ] Ações de CI fixadas por SHA
- [ ] SHA-256 publicado para cada ficheiro descarregável fora de loja
- [ ] O que o produto descarrega depois de instalado está fixado por versão e verificado por hash

## Documentação

- [ ] Instruções de uso atuais (no produto, na loja ou numa página)
- [ ] Limitações conhecidas ditas onde o utilizador as vê, não só nas notas

## Publicação

- [ ] Listagem ou página escrita e aprovada *(Gate 4)*
- [ ] Capturas e vídeo da versão que sai, não de uma anterior
- [ ] Canal de teste da loja (fechado ou interno) passado antes da produção, se a loja o exige
- [ ] Contacto de suporte a funcionar
- [ ] Como se medem instalações e falhas, sem recolher mais do que o `NOTES.md` diz

## Depois

- [ ] `products/<nome>/NOTES.md` atualizado com o que esta release ensinou
- [ ] `projects/<nome>.md` com o marco e a data
- [ ] O que serviu a dois produtos movido para partilhado; o que generaliza, para `knowledge/`
- [ ] ADR para qualquer regra que esta release tenha torcido
