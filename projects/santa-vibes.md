# santa-vibes

**Dono:** DABS
**Estado:** em construção (fase 0 feita; fase 1 em curso, 1.1 a 1.4 feitos; ver `products/santa-vibes/docs/ROADMAP.md`)
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

Em 2026-10-07 DABS aceitou as recomendações para as perguntas em aberto:

3. **CI**: `signals-core` e o crate nativo da `phone-app` no `scripts/check` do produto (cargo test +
   clippy) e por isso na CI; `pc-bridge` (só Windows) e o APK da `phone-app` (NDK) ficam de fora. Feito.
4. **Crossfade**: a cena que sai é renderizada a meio ritmo (`fadeEvery 2`). `ARQUITETURA.md` atualizada. Feito.
5. **Predefinições da box** até haver página de controlo: paleta a rodar (180 s, transição 20 s), sem
   matiz, reatividade 0,7. Feito.
6. **Formato de cena 4**: uniform `uDeltaTime`; `flow-particles` passa a avançar por segundo. Feito.
7. **`image: optional`** quer dizer "pode usar a imagem, nem que seja só a paleta"; `color-field` fica. Feito.
8. **`pc-bridge` arranca com o Windows**, sem janela, com log em ficheiro (`--autostart on`). Ligado no PC
   de DABS. Feito.
9. **Telemóvel**: Claude instala e conduz o teste da `phone-app` por adb; DABS põe música a tocar.
   Feito em 2026-10-07 num Pixel 8 (ver `phone-app/README.md`).
10. **Merge** dos branches empilhados para `main` por PR, depois de a CI passar. Feito: PR #1.
11. **Música de referência**: DABS escolhe 5–10 faixas que ouve, de géneros diferentes, com BPM conhecido.
12. **Nome**: fica o de protótipo até haver intenção de publicar.
13. **Cenas mais reativas** (2026-10-07, DABS): a imagem quase não reage à música (pulso de ~0,35 %
    do brilho aos 123 BPM). Faz-se uma versão mais reativa das 15 cenas, alternável na box com a
    atual sem reinstalar; DABS compara as duas na TV antes de a reativa passar a predefinição.
    Feito (PR #4). 2026-10-07, DABS: **a predefinição é a reativa**; a atual continua a um ▲.

## Por decidir

| # | Pergunta | Quem decide | Bloqueia |
|---|---|---|---|
| 1 | Nome definitivo e identificadores de pacote (decisão 12: só antes de publicar) | DABS | Só uma eventual publicação |

## À espera de DABS (ações, não decisões)

- As faixas de referência (decisão 11), num sítio que o PC leia; ficam fora do repo.

## Próximos passos

- [x] CI verde em `products/santa-vibes` (`scripts/check santa-vibes`) (2026-10-06)
- [x] Repo antigo `davidbrassimoes/santa-vibes` arquivado e pasta local removida (2026-10-06)
- [x] `NOTES.md` do produto criado de `templates/notes.md` (2026-10-06)
- [x] Passo 0.6 na tv-app, medido na box (2026-10-06)
- [x] Fase 1, passo 1.1: `signals-core` (Rust) (2026-10-06)
- [x] Fase 1, passo 1.2: `pc-bridge` (2026-10-06)
- [x] Fase 1, passo 1.3: receção na tv-app (2026-10-06)
- [x] Versão reativa das cenas, alternável na box; predefinição é a reativa (decisão 13) (2026-10-07)
- [ ] Afinar a reação a olho com DABS, com a música de referência (decisão 11)
- [x] Fase 1, passo 1.4: `phone-app`, testada num Pixel 8 (2026-10-07)
- [x] Decisões de 2026-10-07 aplicadas (formato 4, predefinições, CI, arranque do `pc-bridge`)
- [x] Merge para `main` (decisão 10): PR #1, 2026-10-07
- [ ] Fase 1, passo 1.5: MIDI clock e afinação da latência

## Histórico

- 2026-10-06: movido para `dabs-apps`.
- 2026-10-06: CI verde; repo antigo `davidbrassimoes/santa-vibes` arquivado (só leitura).
- 2026-10-06: 0.6 na tv-app; Fase 0 completa. `CLAUDE.md` do produto encurtado; `NOTES.md` criado.
- 2026-10-06: 1.1 `signals-core` (Rust): análise, pacotes, testes com áudio gerado e WAV.
- 2026-10-06: 1.2 `pc-bridge`: loopback WASAPI → UDP; medido no PC, pacotes chegam à box.
- 2026-10-06: 1.3 receção na tv-app: a box reage à música do PC (descoberta, mixer, volta ao Synth).
- 2026-10-06: 1.4 `phone-app` (Kotlin + Rust/JNI): testada no emulador até à box.
- 2026-10-07: DABS aceitou as recomendações para as perguntas em aberto; aplicadas (formato 4, predefinições, CI, arranque do `pc-bridge`).
