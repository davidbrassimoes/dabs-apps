# signals-core — núcleo de DSP em Rust (Fase 1.1)

Biblioteca partilhada por `pc-bridge` (Windows) e `phone-app` (Android, via NDK/JNI).

- Entrada: blocos de amostras PCM (f32, mono ou estéreo, sample rate arbitrário).
- Saída: estrutura `Signals` e serialização do pacote `SIGN` (`docs/PROTOCOLO-SIGNALS.md`).
- FFT (`rustfft`), bandas, onsets, envelope de beat, estimativa de tempo, **previsão de fase**, suavizações "mood", AGC.
- Sem alocações no caminho quente; testes com ficheiros WAV de referência.

> Por implementar.
