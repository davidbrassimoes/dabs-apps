# control-web — página de controlo (Fase 3.2)

HTML, CSS e JS estáticos (`index.html`, `style.css`, `app.js`), sem framework e sem fontes da
internet: a box serve-os e funcionam sem internet. Aberta no telemóvel pelo QR code que o OK mostra
na TV (`http://192.168.1.27:8080/`). Maqueta aprovada a 2026-10-09:
https://claude.ai/artifact/X7ZmWWsTXniPr4y9R6P5ZN.

Quatro separadores (o endereço aceita `#scenes`, `#now`, `#mine`, `#sources`):

- **Cenas**: por estilo; tocar numa muda a TV.
- **Agora**: ◀ ▶, versão atual/reativa, presets do autor e os sliders gerados do `scene.json`;
  guardar um preset meu.
- **Os meus**: presets guardados na box; aplicar, exportar (JSON para o `scene.json`), apagar;
  rotação automática.
- **Fontes**: sinal e reatividade, imagens, perfil, brilho, temperatura, paleta a rodar.

API em `docs/ARQUITETURA.md` (Controlo).

## Como compilar, correr e testar

Não tem build: o `app/build.gradle.kts` da `tv-app` copia esta pasta para os assets do APK
(sem o README). Para ver alterações, recompilar e reinstalar a `tv-app`.

Testar sem telemóvel: abrir `http://192.168.1.27:8080/` no browser do PC com a app aberta na TV.
O Chrome sem janela em Windows não desce abaixo de ~500 px de largura; para ver a largura de um
telemóvel usar a emulação de dispositivo (DevTools ou `Emulation.setDeviceMetricsOverride`).
