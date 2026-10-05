# CLAUDE.md

App Android pessoal de registo de abastecimentos e estatísticas de consumo. Especificação completa em `docs/SPEC.md` — ler antes de qualquer trabalho de funcionalidade.

## Stack
Kotlin · Jetpack Compose · Material 3 · Hilt · Room · Coroutines/Flow · Navigation Compose · CameraX · ML Kit (on-device) · Vico · Glance. Dependências geridas em `gradle/libs.versions.toml` (version catalog); nunca versões hardcoded nos `build.gradle.kts`.

## Comandos
- Build: `./gradlew assembleDebug`
- Testes unitários: `./gradlew testDebugUnitTest`
- Testes instrumentados: `./gradlew connectedDebugAndroidTest`
- Lint: `./gradlew lintDebug`

Correr build e testes unitários antes de dar uma tarefa por terminada.

## Arquitetura
- Pacotes por funcionalidade (`feature/fillup`, `feature/history`, `feature/stats`, `feature/settings`) + `core/` (data, database, domain, ui, scanning).
- UI em Compose sem lógica; ViewModels expõem `StateFlow<UiState>` imutável; eventos via funções.
- Repositórios sobre Room; lógica de cálculo e parsing em funções puras no domínio.
- Hilt para injeção de dependências.

## Regras
- Dinheiro e volumes como inteiros (cêntimos, mililitros, milésimos de cêntimo no preço/litro). Nunca `Float`/`Double` para guardar valores.
- Formatação na UI com locale `pt-PT` (vírgula decimal, €). Textos da UI em português de Portugal, em `strings.xml`.
- App totalmente offline: não adicionar a permissão `INTERNET`.
- Migrações de Room explícitas e testadas; nunca `fallbackToDestructiveMigration` em release.
- Cálculos (consumo, agregados, validações) e parsers (QR da AT, OCR) com testes unitários obrigatórios.
- Valores lidos por câmara são sempre sugestões que o utilizador confirma.
- Preferir APIs estáveis e atuais do Android/Jetpack; evitar dependências desnecessárias.
- Edge-to-edge, suporte a tema claro/escuro e dynamic color.
