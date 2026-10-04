# CLAUDE.md — Santa Vibes

> Nome de protótipo (antes: "Penumbra"). Slug para pastas, repositório, package Android e crates: `santa-vibes` / `santavibes`. Pode mudar; se mudar, atualiza este ficheiro, o README, os cabeçalhos em `shared/glsl/` e os identificadores.

## O que é

Santa Vibes é um sistema de **visuais generativos para criar ambiente e luz** numa sala: corre numa **Google TV Streamer** (Android TV) ligada a uma TV ou, mais tarde, a um **projetor**. Também funciona como **protetor de ecrã** do sistema.

A imagem pode ser:
- **puramente sintetizada** (shaders), ou
- **híbrida**, usando imagens reais (pasta local, acervos open access) como textura/paleta,

e pode ser **reativa** a sinais externos (áudio do PC Windows, microfone ou áudio do telemóvel Android, MIDI clock, hora do dia) ou **não reativa** de todo.

Lê por esta ordem antes de qualquer trabalho:
1. `docs/VISAO.md` — o que se pretende e porquê
2. `docs/ARQUITETURA.md` — módulos, fluxo de dados, contratos
3. `docs/ROADMAP.md` — fases, ordem e critérios de aceitação
4. Os documentos de contrato relevantes para a tarefa:
   - `docs/FORMATO-CENA.md` — como se escreve uma cena (`.frag` + `.json`)
   - `docs/PROTOCOLO-SIGNALS.md` — pacote UDP binário dos sinais
   - `docs/FONTES-IMAGEM.md` — fontes de imagem e licenças
5. `docs/decisoes/` — registo de decisões (ADR). Não reabrir uma decisão sem motivo; se for preciso, escrever um novo ADR que a substitua.

## Regras de ouro

1. **Leveza e desempenho acima de tudo.** A escolha de tecnologia não depende de preferências pessoais de linguagem; depende do que for mais performativo, eficiente e leve. O dono do projeto aceita qualquer linguagem.
2. **Na box: nativo e mínimo.** Kotlin + OpenGL ES 3.0 sobre `GLSurfaceView`. Sem WebView, sem Compose no caminho de render, sem motores de jogo. Cada dependência nova na `tv-app` precisa de justificação escrita (ADR curto).
3. **Zero alocações por frame** no loop de render (Kotlin). Buffers, arrays de uniforms e objetos reutilizados. Nada de lambdas/boxing no `onDrawFrame`.
4. **Orçamento de render**: as cenas renderizam a uma **escala interna** relativa à surface real (na TV atual a surface é 1080p, logo 0.5 = 960×540) e são escaladas; **limite de fps por cena** (30 por defeito para cenas lentas). Escala dinâmica de resolução se o frame time ultrapassar o orçamento.
5. **Fonte única de verdade para as cenas**: `scenes/<id>/scene.frag` + `scenes/<id>/scene.json`, e a biblioteca GLSL em `shared/glsl/`. A `tv-app`, a ferramenta `authoring` e a página `control-web` leem destes ficheiros — nunca há cópias editadas à mão.
6. **As cenas nunca sabem de onde vêm os sinais.** Leem só os uniforms do contrato (`docs/FORMATO-CENA.md`). A mistura entre sinais reais e sinais sintéticos (pela "intensidade de reatividade") é feita no host, antes de enviar uniforms.
7. **Offline-first.** A TV tem de funcionar sozinha: sem PC, sem telemóvel, sem internet (modo sem reatividade + imagens em cache ou cenas sintetizadas).
8. **Contratos versionados.** Qualquer alteração ao formato de cena ou ao protocolo de sinais atualiza o documento respetivo e incrementa a versão.
9. **Sinais em UDP binário**, nunca JSON a 60 Hz. JSON só para controlo/configuração (baixa frequência).

## Convenções

- **Documentação**: português de Portugal.
- **Código, identificadores, comentários e mensagens de commit**: inglês.
- Commits pequenos e descritivos (Conventional Commits: `feat:`, `fix:`, `docs:`, `perf:`, `refactor:`, `chore:`).
- Cada componente tem o seu `README.md` com: como compilar, como correr, como testar.
- Decisões relevantes → novo ficheiro em `docs/decisoes/NNNN-titulo.md`.

## Ambiente do dono do projeto

- **PC**: Windows (é onde corre o Claude Code, a ferramenta de autoria e a bridge de áudio).
- **Box**: Google TV Streamer, Android 14 (API 34), GPU PowerVR GE9215 com GLES 3.2, surface 1920×1080 na TV atual (detalhes em `tv-app/README.md`). Ligada por Ethernet em `192.168.1.27`; `adb connect 192.168.1.27:5555`. Instalação por sideload via `adb`.
- **Telemóvel**: Android.
- **Projetor**: ainda por comprar — o perfil "Projetor" deve ser ajustável sem recompilar.
- O dono testa na box física; quando uma tarefa precisar de validação no hardware, diz exatamente o comando a correr e o que observar (fps, frame time, aquecimento).

## Forma de trabalhar

- Trabalhar **fase a fase** segundo `docs/ROADMAP.md`. Não saltar fases sem acordo.
- No início de cada fase/passo: propor um plano curto, confirmar, depois implementar.
- **Perguntar antes de** adicionar dependências, mudar contratos ou mudar a estrutura de pastas.
- No fim de cada passo: atualizar o `ROADMAP.md` (marcar o que ficou feito) e os READMEs afetados.
- Medir em vez de supor: qualquer otimização vem com números (frame time antes/depois).

## Mapa do repositório

```
docs/            documentação e ADRs
scenes/          cenas (fonte única): <id>/scene.frag + scene.json
shared/glsl/     cabeçalho de uniforms, biblioteca GLSL, passe de saída
shared/profiles/ perfis de saída (tv.json, projector.json)
authoring/       ferramenta de autoria no browser (só desenvolvimento) — Fase 0
tv-app/          app Android TV (Kotlin + GLES 3.0, DreamService) — Fase 0/3
signals-core/    núcleo de DSP em Rust partilhado — Fase 1
pc-bridge/       bridge Windows (WASAPI loopback → UDP) em Rust — Fase 1
phone-app/       app Android emissora de sinais (mic / playback capture) — Fase 1
control-web/     página de controlo para o telemóvel, servida pela tv-app — Fase 3
```

## Comandos

> Preencher à medida que cada componente for criado.

| Componente | Compilar | Correr | Testar |
|---|---|---|---|
| authoring | `cd authoring && npm install && npm run build` | `npm run dev` → http://localhost:5180/ | `npm run check` (tipos) · `node scripts/smoke.mjs` (headless, com o servidor a correr) |
| tv-app | _a definir_ | _a definir_ | _a definir_ |
| signals-core | _a definir_ | — | _a definir_ |
| pc-bridge | _a definir_ | _a definir_ | _a definir_ |
| phone-app | _a definir_ | _a definir_ | _a definir_ |
