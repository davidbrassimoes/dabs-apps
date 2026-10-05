# A barra de entrega

Uma fatia só está feita quando passa tudo isto. **O agente verifica, não pergunta.** Um
produto pode apertar a barra no seu `CLAUDE.md`; nunca a alarga sem ADR.

1. **`scripts/check` verde** para o produto: compila, lint, testes unitários, o que mais o
   produto puser lá. Output só com falhas.
2. **Lógica de negócio com testes.** Cálculos, parsers, validações, permissões, dinheiro,
   datas. Testa-se o que custa caro quando está errado, não a cobertura.
3. **Dados por definir ficam vazios.** Nenhum valor inventado chega a uma UI, a um ficheiro
   de configuração ou a um teste a fazer de real.
4. **Acessibilidade**, quando há interface: navegável por teclado ou comando, contraste
   verificado por teste, leitores de ecrã onde a plataforma os tem. O nível exigido está
   no `SPEC.md`.
5. **Performance é requisito, não polimento.** O `SPEC.md` tem orçamentos concretos para o
   que importa nesse produto (tempo de arranque, frame time, LCP, memória, tamanho do
   binário) e o build ou um teste falha quando são ultrapassados.
6. **Dados pessoais listados** no `NOTES.md`, cada um com finalidade e retenção. Sem
   terceiros que recolham dados sem decisão escrita.
7. **Dependências com licença registada** no `SPEC.md`. Nada copyleft sem ADR.
8. **Segredos fora do repo**, e nada que os revele em logs, testes ou commits.
9. **i18n**, quando o produto tem mais de uma língua: texto visível todo por chaves, línguas
   em paridade.
10. **`NOTES.md` atualizado**: o que mudou, o que falta, decisões tomadas, o que a próxima
    sessão precisa de saber.

## Antes de publicar

A barra acima é por fatia. Publicar tem a sua: `templates/release-checklist.md`, copiado
para `products/<nome>/RELEASE.md` e preenchido. Nada sai com uma caixa por marcar.

## Performance agêntica

Não se contam tokens, mas mede-se o que os gasta. Se um produto precisa de ler mais de
~5 ficheiros para uma alteração simples, a estrutura é o problema: dividir, renomear ou
documentar no `CLAUDE.md` do produto. Se um comando produz mais de um ecrã de output quando
passa, está mal escrito. Se uma sessão começa a reler a conversa anterior, o `NOTES.md`
falhou.
