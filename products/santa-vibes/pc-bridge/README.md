# pc-bridge — emissor de sinais no Windows (Fase 1.2)

Binário Rust único: captura por loopback WASAPI (`cpal`), análise via `signals-core`, envio UDP unicast para a TV descoberta pelo beacon. MIDI clock via `midir` (Fase 1.5).

Metas: < 2 % CPU, < 20 MB RAM, arranque automático opcional.

> Por implementar.
