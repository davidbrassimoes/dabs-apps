# CLAUDE.md — Santa Vibes

> Nome de protótipo (antes: "Penumbra"). Slug `santa-vibes` / `santavibes`. Se mudar: este ficheiro, o README, os cabeçalhos em `shared/glsl/` e os identificadores.

Visuais generativos para criar ambiente e luz numa sala, numa **Google TV Streamer** (Android TV) ligada a uma TV ou a um projetor; também protetor de ecrã. Imagem sintetizada (shaders) ou híbrida (imagens reais como textura/paleta), reativa a sinais externos (áudio do PC, telemóvel, hora) ou não.

Ler por esta ordem, só o que a tarefa pede:
1. `docs/VISAO.md`, `docs/ARQUITETURA.md`, `docs/ROADMAP.md`
2. Contratos: `docs/FORMATO-CENA.md`, `docs/PROTOCOLO-SIGNALS.md`, `docs/FONTES-IMAGEM.md`
3. `docs/REPO.md` — mapa, comandos, convenções, ambiente (box em `192.168.1.27`)
4. `docs/decisoes/` — ADRs. Não reabrir sem motivo; um ADR novo substitui o antigo.
5. `NOTES.md` — onde a última sessão parou.

## Regras de ouro

1. **Leveza e desempenho acima de tudo.** A tecnologia escolhe-se pelo que é mais performativo e leve, não por preferência.
2. **Na box: nativo e mínimo.** Kotlin + OpenGL ES 3.0 sobre `GLSurfaceView`. Sem WebView, Compose no render ou motores de jogo. Dependência nova na `tv-app` precisa de ADR curto.
3. **Zero alocações por frame** no loop de render. Nada de lambdas/boxing no `onDrawFrame`.
4. **Orçamento de render**: escala interna por cena, limite de fps por cena (30 por defeito), escala dinâmica se o frame time passar o orçamento.
5. **Fonte única das cenas**: `scenes/<id>/scene.frag` + `scene.json` e `shared/glsl/`. Nunca cópias editadas à mão.
6. **As cenas não sabem de onde vêm os sinais.** Só leem os uniforms do contrato; a mistura real/sintético faz-se no host.
7. **Offline-first.** A TV funciona sozinha, sem PC, telemóvel ou internet.
8. **Contratos versionados.** Mudar formato de cena ou protocolo atualiza o documento e a versão.
9. **Sinais em UDP binário**, nunca JSON a 60 Hz. JSON só para controlo.

## Forma de trabalhar

- Fase a fase segundo `docs/ROADMAP.md`; não saltar fases sem acordo. Plano curto no início de cada passo.
- **Perguntar antes de** adicionar dependências, mudar contratos ou mudar a estrutura de pastas.
- No fim de cada passo: `ROADMAP.md`, os READMEs afetados e `NOTES.md`.
- Medir em vez de supor: otimizações vêm com números (frame time antes/depois), medidos na box.

## Neste workspace

Vive em `dabs-apps/products/santa-vibes/` desde 2026-10-06. As regras da raiz (`../../CLAUDE.md`) aplicam-se; este ficheiro tem prioridade onde discordam. Antes de dar uma tarefa por terminada: `scripts/check` (ou, da raiz, `scripts/check santa-vibes`). Estado do projeto em `../../projects/santa-vibes.md`.
