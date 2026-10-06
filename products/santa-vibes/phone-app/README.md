# phone-app — emissor de sinais no Android (Fase 1.4)

App Android (Kotlin, sem dependências de runtime, minSdk 29) que capta som, analisa-o com o
`signals-core` (Rust, via JNI) e envia pacotes `SIGN` à TV, como o `pc-bridge`.

Modos:
- **Microfone** — som da sala; serviço em primeiro plano do tipo `microphone`. Usa a fonte
  `UNPROCESSED` quando o telemóvel a tem (sem supressão de ruído nem AGC de voz), senão `MIC`.
- **Som do telemóvel** — o que as outras apps tocam (`AudioPlaybackCapture`); o Android pede
  consentimento em cada sessão (escolher "ecrã inteiro"); apps que recusam captura ficam de fora.

Nada é gravado nem sai da rede local: o áudio só existe em memória, dentro da análise; à TV só
chegam os sinais (bandas, ritmo, mood). A notificação diz isso e tem o botão "Parar".

## Estrutura

```
app/      Kotlin: MainActivity (UI em código), CaptureService (AudioRecord + envio), Sender (UDP,
          beacon), SignalsNative (JNI), Status (o que o ecrã mostra)
native/   Rust cdylib `santavibes_signals`: signals-core atrás de um mutex + 4 funções JNI. Sem
          crate de JNI: as 3 funções do JNIEnv usadas vêm da tabela de funções JNI (índices fixos
          pela especificação: 171, 222, 223), ver native/src/jni.rs
```

## Como compilar, correr e testar

Requer: JDK 17+, Android SDK (platform 36) e **NDK 30.0.16248370**, Rust (`rustup`) com os
targets Android e o `cargo-ndk`:

```
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
```

O NDK instala-se com `sdkmanager --package_file=<ficheiro>` (no Windows, o `sdkmanager.bat` parte
os nomes com `;` se forem passados na linha de comandos).

```
cd phone-app
./gradlew :app:assembleDebug          # corre o cargo-ndk (3 ABIs) e junta as .so ao APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
cd native && cargo test --release     # o wrapper no PC (sem JNI)
```

Na app: "Microfone" ou "Som do telemóvel"; a TV é encontrada pelo beacon (Wi-Fi, a app segura um
`MulticastLock`) ou pelo IP escrito no campo.

## Testado em 2026-10-06 (emulador Android 16, x86_64; sem telemóvel real)

| | Resultado |
|---|---|
| JNI | as três funções da tabela respondem; nada rebentou em ~5 min de captura |
| Microfone → `fake_tv` no PC | 60–63 pkt/s, 0 perdidos com áudio |
| Microfone → box (`192.168.1.27`) | a box mostra `src=mic`, peso 1,00, 62 pkt/s, 0 B/s, cena no limite de fps |
| Silêncio | 10 pkt/s com a flag de silêncio (pelo NAT do emulador perdem-se ~1 em 50) |
| Som do telemóvel | consentimento ("ecrã inteiro"), serviço `mediaProjection` e envio funcionam |

**Não verificado:**
- O microfone do emulador deu um sinal sintético constante (energia 1,00, 101,9 BPM fixos), não o
  som da sala: a análise de um microfone real está por testar.
- "Som do telemóvel" com música real: o Chrome do emulador tocou o loop mas a captura recebeu
  silêncio (o Chrome toca por AAudio e é provável que fique fora da captura). Falta testar com
  uma app de música num telemóvel.
- A descoberta pelo beacon (o emulador está atrás de NAT; testou-se com o IP escrito).
- No emulador, escrever no campo do IP por `adb input` deixou de funcionar depois da primeira vez;
  ver se acontece num telemóvel.

## Por fazer

- Testes num telemóvel real (os três pontos acima).
- Microfone: filtrar voz e picos curtos (ARQUITETURA.md) — fica para a afinação, se fizer falta.
- Ícone e nome definitivos (dependem do nome do produto).
