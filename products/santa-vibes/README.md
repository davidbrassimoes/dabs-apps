# Santa Vibes

Visuais generativos para criar ambiente e luz numa sala — na TV (Google TV Streamer) ou num projetor. Sintetizados ou a partir de imagens reais, reativos à música ou simplesmente a evoluir sozinhos.

> **Estado:** scaffold inicial. Nada está implementado ainda — ver `docs/ROADMAP.md`.

## Começar

1. Instala os pré-requisitos (abaixo).
2. Abre esta pasta no Claude Code.
3. Cola o conteúdo de `PROMPT-INICIAL.md` como primeira mensagem.

## Pré-requisitos (Windows)

Nem todos são precisos logo na Fase 0 — ver `docs/ROADMAP.md`.

| Ferramenta | Para quê | Fase |
|---|---|---|
| Git | controlo de versões | 0 |
| Node.js LTS | ferramenta de autoria (só desenvolvimento) | 0 |
| JDK 17+ e Android SDK (Android Studio ou command-line tools) | `tv-app` e `phone-app` | 0 |
| `adb` (platform-tools) | instalar na box e no telemóvel | 0 |
| Rust (rustup, toolchain MSVC) | `signals-core`, `pc-bridge` | 1 |
| Android NDK + `cargo-ndk` | `signals-core` no telemóvel | 1 |

### Ligar a box por adb (Wi-Fi)

1. Na box: Definições → Sistema → Acerca de → carregar 7× em "Compilação do SO Android TV" para ativar o modo de programador.
2. Definições → Sistema → Opções de programador → ativar **Depuração USB** / **Depuração sem fios**.
3. No PC: `adb connect <ip-da-box>:5555` (ou o fluxo de emparelhamento por código, se a box o pedir) e aceitar o pedido no ecrã da TV.

## Documentação

- `CLAUDE.md` — instruções para o Claude Code (e um bom resumo para humanos)
- `docs/VISAO.md` · `docs/ARQUITETURA.md` · `docs/ROADMAP.md`
- `docs/FORMATO-CENA.md` · `docs/PROTOCOLO-SIGNALS.md` · `docs/FONTES-IMAGEM.md`
- `docs/decisoes/` — decisões registadas
