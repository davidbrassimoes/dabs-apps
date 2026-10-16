# pc-bridge — emissor de sinais no Windows (Fase 1.2)

Executáveis Rust (~1,5 MB cada, sem runtime; `pc-bridge` com consola e `pc-bridge-bg` sem janela): captura o que o PC está a tocar (loopback WASAPI
via `cpal`), analisa com `signals-core` e envia pacotes `SIGN` por UDP unicast para cada TV que
encontra pelo beacon `PNMB` (ou dada à mão).

Metas: < 2 % CPU, < 20 MB RAM (cumpridas, ver em baixo), arranque automático (feito em 2026-10-07, ver em baixo).

## Como compilar, correr e testar

Requer Rust stable (`rustup`). Dependências: `signals-core` (pasta ao lado) e `cpal` (ADR `0001`).

```
cd pc-bridge
cargo build --release                       # target/release/pc-bridge.exe
target/release/pc-bridge.exe --list         # dispositivos de saída (* = o por defeito)
target/release/pc-bridge.exe                # captura a saída por defeito, espera pelo beacon da TV
target/release/pc-bridge.exe --tv 192.168.1.27 [--device "Realtek"] [--lookahead -10] [--ws 47810|off] [--quiet]
cargo run --release --example fake_tv       # finge ser a TV: beacon + mostra o que chega, 1 linha/s
```

De 5 em 5 s a bridge escreve uma linha: áudio/silêncio, bpm (`?` = ainda sem tempo), energia,
bandas, beats/s e pacotes/s enviados. Na primeira vez o Windows pode pedir autorização da
firewall para o `pc-bridge.exe` (escuta o beacon em UDP 47801): permitir em redes privadas.

## Arranque com o Windows (decisão de DABS, 2026-10-07)

Há dois executáveis com o mesmo código: `pc-bridge` (consola) e `pc-bridge-bg` (sem janela; escreve em
`%LOCALAPPDATA%\SantaVibes\pc-bridge.log`, uma linha de estado por minuto; acima de 1 MB o log passa a
`pc-bridge.log.old`).

```
target/release/pc-bridge.exe --autostart on    # copia o pc-bridge-bg.exe para %LOCALAPPDATA%\SantaVibes\,
                                               # regista-o em HKCU\...\CurrentVersion\Run e arranca-o
target/release/pc-bridge.exe --autostart off   # pára-o e tira o registo
```

Depois de recompilar, correr `--autostart on` outra vez instala a versão nova. Com o de fundo a correr,
uma bridge de consola não consegue escutar o beacon (porto ocupado): usar `--tv` ou desligar o de fundo.
A firewall pode pedir autorização para o `pc-bridge-bg.exe` na primeira vez (é outro caminho).
O processo de fundo arranca desligado do chamador (sem consola, stdio para NUL): um script que leia o
output do `--autostart on` recebe o fim do output e o código de saída logo.
Ligado no PC de DABS em 2026-10-07.

## Comportamento

- **Captura**: a saída por defeito do Windows, ou a primeira cujo nome contém `--device`. Se a
  saída por defeito mudar (ex.: auscultadores), muda com ela ao fim de ≤ 2 s. Se o dispositivo
  desaparecer, tenta de novo de 2 em 2 s.
- **Silêncio**: com nada a tocar, o WASAPI não entrega áudio; a bridge alimenta a análise com
  zeros, por isso a flag de silêncio liga-se e o envio baixa para 10 Hz (protocolo).
- **Erros ignorados**: o loopback reporta um "underrun/overrun" sempre que a reprodução começa
  ou pára, e `DeviceChanged` quando ele próprio reencaminha; nenhum obriga a reabrir.
- **Destinos**: TVs vistas pelo beacon (esquecidas após 7 s sem beacon) mais as dadas com `--tv`
  (porto 47800 se não indicado). Se o porto 47801 estiver ocupado (outra bridge), só `--tv`.
- **Threads**: a callback do `cpal` corre a análise (sem alocações, ~20 µs por 10 ms de áudio)
  sob um mutex; o fio principal copia os `Signals` a 60 Hz e envia; um fio escuta beacons.

## Medido em 2026-10-06 (PC do dono, Realtek 48 kHz estéreo, Windows 11)

Bridge e `fake_tv` no mesmo PC, dois loops reais tocados pelas colunas (piano elétrico 123 BPM,
31 s; rock, 17 s), 60 s no total:

| | Resultado |
|---|---|
| Pacotes | 60/s com áudio, 10/s em silêncio, **0 perdidos, 0 fora de ordem**, intervalo máximo 17–24 ms |
| Tempo | piano 122,9–124,3 BPM (2 beats/s); rock 101,7–104,1 |
| CPU | **1,6 % de um núcleo** (0,10 % da máquina, 16 núcleos lógicos) |
| Memória | **11,9 MB** working set, 2,1 MB privada |

Até à box: `--tv 192.168.1.27` com `nc -u -l -p 47800` na box (adb) recebeu 69 pacotes de 56
bytes com magic `SIGN`, versão 1, fonte 1 (PC loopback). Falta a receção na tv-app (1.3).

**Corrigido 2026-10-08:** o falso 181,5 BPM no primeiro segundo de música (ver `signals-core/README.md`).

## Por fazer

- Ícone na área de notificação, se fizer falta.
- (feito 2026-10-08) Feed WebSocket local para o authoring (`src/ws.rs`, `--ws`), sem dependência nova.

## Latência (`--lookahead`), 1.5

Por defeito **-10 ms** (`PC_LOOKAHEAD_MS` em `src/bridge.rs`). O valor é medido, não estimado.

- **Como se mede:** o Pixel 8 grava a TV e o som das colunas do PC no mesmo vídeo, enquanto o PC
  toca cliques a 115 BPM e a TV mostra o `halo` na versão reativa. A 120 BPM o beat cairia sempre
  no mesmo frame a 30 fps.
- **Como se analisa:** a hora de cada clique sai da correlação do áudio do vídeo com a faixa
  inteira. A fase do pico de brilho da TV dentro de cada beat sai da primeira harmónica. Os
  scripts estão descritos em `NOTES.md`.
- **2026-10-07, com 60 ms:** o pico de brilho chegou **71 ± 6 ms antes** do clique ouvido (15
  blocos de 8 beats). Daí -10 ms.
- **Porque é negativo:** o loopback vê o áudio antes do buffer de saída e das colunas, e a TV
  mostra mais depressa do que o som chega à sala.
- **Confirmado 2026-10-08, com -10 ms:** cliques a 115 BPM, -13 ms (blocos -21 ± 26); cliques a 73 BPM, +20 ms
  (+18 ± 18; tempo lento, sem a ambiguidade de meio beat); loop de rock (102,95 BPM), +20 ms (+33 ± 36). Tudo
  dentro de um frame a 30 fps; a média (+9 ms) é menor que a variação, por isso fica -10.
  Cuidado ao medir: uma gravação com o telemóvel a mexer deu um falso "meio beat".
- **Depende da sala:** outras colunas, Bluetooth ou o projetor mudam o valor. A `phone-app` fica
  em 60 ms, porque esse caminho não foi medido.
