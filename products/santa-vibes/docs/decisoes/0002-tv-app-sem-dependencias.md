# 0002 — tv-app sem dependências de runtime, dividida em `core` e `app`

- **Estado:** aceite
- **Data:** 2026-10-05

## Contexto

A regra 2 do `CLAUDE.md` pede uma box "nativa e mínima" e obriga a justificar cada dependência nova. A arquitetura também pede que a lógica de host que não é render (composição de shaders, drift, variação de paleta, `Synth`, mistura de sinais) fique livre de UI, para poder servir outros hosts mais tarde.

## Decisão

- `tv-app/` é um projeto Gradle com dois módulos:
  - `core`: Kotlin/JVM puro, sem Android. Modelo de cena, leitor JSON mínimo, composição de shaders (e, no 0.6, drift e paleta). Testado com JUnit na JVM, contra as **mesmas fixtures** da autoria (`authoring/test/fixtures/*.json`).
  - `app`: Android, com `android.app.Activity`, `GLSurfaceView` e GLES 3.0, tudo da plataforma.
- **Dependências de runtime: nenhuma**, nem AndroidX. O `scene.json` é lido por um leitor JSON próprio (~150 linhas, só em tempo de carga), em vez de `org.json` ou de uma biblioteca.
- **Só de build e de teste:** Android Gradle Plugin 9.4 (Kotlin incluído), plugin Kotlin JVM 2.4 para o `core`, JUnit 4.13 nos testes.

## Alternativas rejeitadas

- **AndroidX (AppCompat, Leanback, Compose):** megabytes e inicialização a mais para uma Activity em ecrã inteiro sem UI. O launcher de TV só precisa de um banner no manifest.
- **`org.json` da plataforma:** existe no Android, mas não na JVM dos testes (aí é um stub). Obrigaria a uma dependência de teste, e o `core` deixaria de ser portável.
- **Um só módulo Android:** os testes das fixtures exigiriam Robolectric ou um dispositivo.

## Consequências

- O APK fica pequeno e arranca depressa. Não há atualizações de bibliotecas a seguir.
- O `core` pode ser reaproveitado por outro host JVM/Android (por exemplo, a `phone-app` em modo renderer).
- Qualquer dependência de runtime futura precisa de um novo ADR.
