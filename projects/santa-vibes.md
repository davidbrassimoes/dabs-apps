# santa-vibes

**Dono:** DABS
**Estado:** em construção (fase 0, ver `products/santa-vibes/docs/ROADMAP.md`)
**Destino:** pessoal; sideload na Google TV Streamer. Se mudar, ver ADR-001 (5).
**Pasta:** `products/santa-vibes/`

## Onde está

Visuais generativos para Android TV (Kotlin + OpenGL ES), com ferramenta de autoria no
browser, bridge de áudio no PC e núcleo de sinais em Rust previstos. É um monorepo dentro do
monorepo: cada componente tem o seu README com build, execução e teste. Documentação e ADRs
próprios em `products/santa-vibes/docs/`; mantêm-se. Último trabalho: soak de 31 minutos na
cena nebula a 30 fps (`07d85cd`, 2026-10-05).

## Decisões do projeto

1. 2026-10-06, DABS: entra no `dabs-apps` com a história do repo `davidbrassimoes/santa-vibes`;
   o repo antigo arquiva-se quando a CI aqui estiver verde.
2. O `CLAUDE.md` do produto (86 linhas) fica como está por agora; encurtar para ~40 quando
   houver uma sessão nele, movendo o mapa do repo e as convenções para `docs/`.

## Por decidir

| # | Pergunta | Quem decide | Bloqueia |
|---|---|---|---|
| 1 | Nome definitivo (hoje nome de protótipo) e identificadores de pacote | DABS | Só uma eventual publicação |
| 2 | Se `phone-app`, `pc-bridge` e `signals-core` entram na CI quando tiverem build | DABS | Nada por agora |

## Próximos passos

- [x] CI verde em `products/santa-vibes` (`scripts/check santa-vibes`) (2026-10-06)
- [x] Repo antigo `davidbrassimoes/santa-vibes` arquivado e pasta local removida (2026-10-06)
- [ ] `NOTES.md` do produto criado de `templates/notes.md` na próxima fatia

## Histórico

- 2026-10-06: movido para `dabs-apps`.
- 2026-10-06: CI verde; repo antigo `davidbrassimoes/santa-vibes` arquivado (só leitura).
