# santa-vibes

**Dono:** DABS
**Estado:** em construção (fase 0 feita; fase 1 em curso, 1.1 a 1.3 feitos, 1.4 à espera de um telemóvel real; ver `products/santa-vibes/docs/ROADMAP.md`)
**Destino:** pessoal; sideload na Google TV Streamer. Se mudar, ver ADR-001 (5).
**Pasta:** `products/santa-vibes/`

## Onde está

Visuais generativos para Android TV (Kotlin + OpenGL ES), com ferramenta de autoria no
browser, bridge de áudio no PC e núcleo de sinais em Rust previstos. É um monorepo dentro do
monorepo: cada componente tem o seu README com build, execução e teste. Documentação e ADRs
próprios em `products/santa-vibes/docs/`; mantêm-se. Último trabalho: passo 0.6 na tv-app (drift,
variação de paleta, crossfade), medido na box, 2026-10-06. Notas em `products/santa-vibes/NOTES.md`.

## Decisões do projeto

1. 2026-10-06, DABS: entra no `dabs-apps` com a história do repo `davidbrassimoes/santa-vibes`;
   o repo antigo arquiva-se quando a CI aqui estiver verde.
2. O `CLAUDE.md` do produto encurta-se para ~40 linhas, com o mapa do repo e as convenções em
   `docs/`. Feito em 2026-10-06: 35 linhas, resto em `docs/REPO.md`.

## Por decidir

| # | Pergunta | Quem decide | Bloqueia |
|---|---|---|---|
| 1 | Nome definitivo (hoje nome de protótipo) e identificadores de pacote | DABS | Só uma eventual publicação |
| 2 | Se `phone-app`, `pc-bridge` e `signals-core` entram na CI quando tiverem build. Já é concreto: `signals-core` compila e tem 24 testes; `pc-bridge` compila (só Windows: precisa de runner Windows na CI ou fica de fora) (`cargo test`, ~5 s depois de compilado); entrar implica `scripts/check` do produto chamar o `cargo` e a CI instalar Rust | DABS | Nada; hoje o Rust não é verificado pelo `scripts/check` |
| 3 | Durante o crossfade, a cena que sai: continua a mexer (hoje, como diz `ARQUITETURA.md`; 6/15 transições a 24–26 fps), a meio ritmo (13/15 no limite) ou parada no último frame (15/15). Números em `tv-app/README.md`. Mudar atualiza `ARQUITETURA.md` | DABS | Nada; muda uma linha (`fadeEvery`) |
| 4 | Valores por defeito na box até haver página de controlo: rotação de paleta e matiz (hoje desligados, como no authoring) e reatividade (hoje 1: com o PC a tocar, a imagem segue só o áudio real) | DABS | Nada; até lá, extras do intent |
| 5 | `flow-particles` depende do fps (avança por frame): acrescentar `uDeltaTime` ao formato de cena? | DABS | Nada; nota em `NOTES.md` |
| 6 | `color-field` tem `image: optional` mas não lê `uImage`: `optional` quer dizer "paleta da imagem" ou "amostra a imagem"? | DABS | Fase 2 |
| 7 | Música de referência para afinar o tempo e as bandas: 5–10 faixas que DABS ouve de facto, de géneros diferentes, com o BPM conhecido (WAV ou outro formato; ficam fora do repo) | DABS | A afinação no 1.2/1.5, não o código |
| 8 | `pc-bridge` arranca com o Windows? (atalho em `shell:startup`, sem consola, log em ficheiro) | DABS | Nada; corre-se à mão |
| 9 | Testar a `phone-app` no telemóvel ligado ao PC (`3B241FDJH000QX`): instalo e conduzo eu por adb, ou fazes tu à mão? | DABS | Fechar o 1.4 |
| 10 | Merge dos branches empilhados `santa-vibes-0.6` → `-1.4` para `main` | DABS | Nada; cada passo novo empilha mais |

## Próximos passos

- [x] CI verde em `products/santa-vibes` (`scripts/check santa-vibes`) (2026-10-06)
- [x] Repo antigo `davidbrassimoes/santa-vibes` arquivado e pasta local removida (2026-10-06)
- [x] `NOTES.md` do produto criado de `templates/notes.md` (2026-10-06)
- [x] Passo 0.6 na tv-app, medido na box (2026-10-06)
- [x] Fase 1, passo 1.1: `signals-core` (Rust) (2026-10-06)
- [x] Fase 1, passo 1.2: `pc-bridge` (2026-10-06)
- [x] Fase 1, passo 1.3: receção na tv-app (2026-10-06)
- [ ] Afinar a reação a olho com DABS, com a música de referência (pergunta 7)
- [~] Fase 1, passo 1.4: `phone-app` feita e testada no emulador; falta telemóvel real (pergunta 9)
- [ ] Fase 1, passo 1.5: MIDI clock e afinação da latência

## Histórico

- 2026-10-06: movido para `dabs-apps`.
- 2026-10-06: CI verde; repo antigo `davidbrassimoes/santa-vibes` arquivado (só leitura).
- 2026-10-06: 0.6 na tv-app; Fase 0 completa. `CLAUDE.md` do produto encurtado; `NOTES.md` criado.
- 2026-10-06: 1.1 `signals-core` (Rust): análise, pacotes, testes com áudio gerado e WAV.
- 2026-10-06: 1.2 `pc-bridge`: loopback WASAPI → UDP; medido no PC, pacotes chegam à box.
- 2026-10-06: 1.3 receção na tv-app: a box reage à música do PC (descoberta, mixer, volta ao Synth).
- 2026-10-06: 1.4 `phone-app` (Kotlin + Rust/JNI): testada no emulador até à box.
