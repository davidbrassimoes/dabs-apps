# phone-app — emissor de sinais no Android (Fase 1.4)

Kotlin (UI mínima, foreground service, captura) + `signals-core` via NDK/JNI.

Modos:
- **Microfone** — som da sala; foreground service do tipo `microphone`.
- **Playback capture** — áudio do próprio telemóvel (`AudioPlaybackCapture`, Android 10+); consentimento por sessão; algumas apps bloqueiam a captura.

> Por implementar.
