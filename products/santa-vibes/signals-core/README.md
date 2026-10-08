# signals-core — núcleo de DSP em Rust (Fase 1.1)

Biblioteca partilhada por `pc-bridge` (Windows) e `phone-app` (Android, via NDK/JNI). Recebe
PCM, devolve `Signals` normalizados e serializa o pacote `SIGN` (`docs/PROTOCOLO-SIGNALS.md` v1).
Uma dependência: `rustfft` (ADR `docs/decisoes/0001`). **Nada aloca depois de `Analyzer::new`**
(há um teste com um alocador que conta).

## Como compilar, correr e testar

Requer Rust stable (instalado com `rustup`; testado com 1.99, `x86_64-pc-windows-msvc`).

```
cd signals-core
cargo test --release            # 24 testes: unidades, análise de áudio gerado, WAV, zero alocações
cargo clippy --all-targets      # sem avisos
cargo run --release --example analyze -- musica.wav [--csv out.csv] [--lookahead 60]
```

O `analyze` corre um WAV (PCM 16/24/32 ou float 32, qualquer sample rate e número de canais)
como faria um emissor a 60 Hz e imprime uma linha por segundo (bpm, confiança, bandas, mood,
beats) e o custo por frame de análise. Com `--csv` grava todos os pacotes, para gráficos.

## Uso

```rust
let mut a = Analyzer::new(48_000, Config::default());
// callback de captura:
a.push_interleaved(&samples, channels);
// a ~60 Hz:
let s = a.take();   // limpa o latch de onset
let pkt = packet::encode_sign(&s, SourceKind::PcLoopback, packet::flags_of(&s), seq, now_ms);
```

## Algoritmo

Por frame de análise (hop = FFT/4: 512 amostras a 44,1/48 kHz, ~86–94 frames/s; 256 a 16 kHz):

| Etapa | O quê | Constantes |
|---|---|---|
| Espectro | Janela de Hann, FFT de 2048 (44,1/48 kHz) ou 1024 (16 kHz), ~40 ms | — |
| Bandas | Nível médio quadrático por banda em dBFS (seno a escala total = −3 dB) | low 20–250 Hz, mid 250–2000, high 2000–12000; energy = as três |
| AGC | Janela de 36 dB abaixo de um pico adaptativo (ataque ~50 ms, descida 1 dB/s, pico ≥ −50 dBFS). Cada banda tem o pico preso a ≥ (pico geral − 20 dB): banda vazia lê 0 | `BAND_SPAN_DB` 20 |
| Suavização rápida | Um polo, subida 15 ms, descida 150 ms | — |
| Centroid | Centroide espectral, escala log: 150 Hz → 0, 6 kHz → 1; suavizado 100 ms | — |
| Silêncio | Nível geral < −60 dBFS: bandas a 0, flag de silêncio, sem beats | `Config::silence_db` |
| Função de onsets | Fluxo espectral (só subidas) de `ln(1 + 100·|X|)`, 30 Hz–5 kHz | — |
| Onsets | Máximo local acima de média + 3 desvios do último segundo, ≥ 0,1 s entre eles | `ONSET_K` 3 |
| Tempo | Autocorrelação dos últimos 6 s da função de onsets (centrada, suavizada em triângulo de 5), de 0,25 em 0,25 s; 60–180 BPM; score = prior × (acf[L] + 0,5·acf[2L]), prior log-gaussiano de 1 oitava em 120 BPM; refinamento parabólico. Tempo novo precisa de 3 estimativas seguidas (2 sem tempo); válido com confiança > 0,3 | `CONFIDENCE_MIN` 0,3 |
| Fase | Pente sobre os últimos 8 beats (pesos 0,8^k) diz há quantos frames foi o último beat; PLL com ganho 0,35 | — |
| Beat | Com tempo: dispara quando a fase prevista (`lookahead_ms` à frente) dá a volta. Sem tempo: em cada onset. Envelope decai com τ = 0,18 s (igual ao `Synth` da tv-app) | `Config::lookahead_ms` 60 |
| Mood | AGC lento (descida 0,1 dB/s) da energia, e o centroid, médias de τ = 30 s | — |

## O que foi verificado (2026-10-06)

Com áudio gerado (kick com queda de tom, snare, hi-hats em colcheias, nota sustentada, ruído),
a 16, 44,1 e 48 kHz:

- Tempo a ±2 %: 90, 120, 128 e 150 BPM só com kicks; 96, 124 e 140 com o groove completo (não
  confunde com os hi-hats); muda de 100 para 130 BPM em menos de 6 s.
- Com `lookahead_ms` 0, os beats disparados caem a menos de 40 ms dos verdadeiros; 100 ms de
  lookahead adiantam-nos 100 ± 25 ms.
- Ruído constante: sem tempo e 1 onset em 10 s. Silêncio: tudo a 0 e flag de silêncio.
- AGC: o mesmo groove a −6 dB e a −30 dB dá energia média com menos de 0,15 de diferença.
- Pacotes `SIGN`/`PNMB` byte a byte contra a tabela do protocolo; WAV escrito e lido de volta dá o mesmo tempo.
- **Custo**: ~18 µs por frame de análise no PC (release), ~0,16 % de um núcleo.

Com loops reais (WAVs de freesound na pasta Downloads do dono, não estão no repo): o único com
tempo no nome (piano elétrico, 123 BPM) dá 123,3–124,1. Nos loops de bateria fixa 75, 65, ~102 e
~126–130 BPM; sem o tempo verdadeiro, não sei se 65 e 75 são metade do real.

## Afinação com música real (2026-10-08)

8 faixas da pasta de música de DABS (hip-hop, rock, eletrónica, electro, folk, jazz, hardcore, rock
lento ao vivo) mais os dois loops de teste, cada uma com 5 s de silêncio antes. BPM de referência
de um estimador offline independente (faixa inteira). Ver `NOTES.md` para a tabela.

- **O falso 181,5 BPM** era o topo do intervalo de lags: a passagem de silêncio para música não
  tem pico e o máximo caía na ponta. Aparecia em 8 de 10 faixas; agora 0. Um máximo numa ponta não
  conta.
- **Metade / dobro:** depois de fixado, o dobro ou a metade confirma o tempo em vez de o trocar.
  Mudanças de nível: 76 → 23 (0 nas faixas com batida clara).
- O primeiro tempo pede 4 estimativas iguais (era 2): fixa ~0,5 s mais tarde.
- 3 s de silêncio esquecem o tempo (música nova).
- **Fica por resolver:** hardcore e jazz acertam ~50 % (referência também incerta); introduções em
  tercina dão 4:3 nos primeiros segundos; a faixa lenta ao vivo fixa só ao fim de ~12 s.

## O que falta

- Tempo pelo microfone do telemóvel: repetir o teste com estas faixas.
