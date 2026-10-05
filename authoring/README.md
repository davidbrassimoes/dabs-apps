# authoring — ferramenta de autoria (Fase 0.2)

Página WebGL2 **só para desenvolvimento**: iterar cenas no PC com hot reload. Não vai para a box.

- Lê `../scenes/*` e `../shared/*` diretamente do disco — nunca copia nem edita cópias.
- Composição de shaders igual à que a `tv-app` terá de fazer (`src/compose.ts` é a implementação de referência de `docs/FORMATO-CENA.md`).
- Painel de parâmetros gerado do `scene.json`; seletor de cena, de **preset** (`presets` do `scene.json`, formato 2) e de perfil; editor da paleta (`uPalette`); botão **Copiar como preset**, que põe na área de transferência (e na consola) o JSON dos valores e da paleta atuais, pronto a colar em `presets`; sinais sintéticos (LFOs ou sliders manuais, metrónomo com BPM e botão Tap).
- `feedback` (ping-pong RGBA16F, fallback RGBA8), `renderScale`, limite de fps, passe de saída (`shared/glsl/output.frag`) com os valores do perfil ajustáveis em runtime.
- Resolução **simulada da box** (1080p por defeito) para que `uResolution` e o dithering sejam os mesmos que na TV.
- Erros de compilação mapeados para o ficheiro e linha originais (`scene.frag`, `lib/noise.glsl`, …).
- Largar uma imagem na página alimenta `uImage` (cenas com `image` ≠ `none`).
- Teclas: ← → trocam de cena (como o D-pad na box), espaço pausa.

## Dependências

Só de desenvolvimento, nenhuma em runtime:

| Pacote | Para quê |
|---|---|
| `esbuild` | transpilar TypeScript para um único bundle ES2022 (`dist/main.js`), em modo watch |
| `typescript` | só `tsc --noEmit` (verificação de tipos; o esbuild não verifica) |

O servidor de desenvolvimento (`dev-server.mjs`) usa apenas módulos do Node: serve a página, expõe `scenes/` e `shared/` em leitura, lista cenas/perfis em `/api/*` e envia alterações por Server-Sent Events (`/events`) com `fs.watch`. Preferiu-se isto ao Vite porque o hot reload de ficheiros que não são importados pelo bundle exigiria um plugin de qualquer forma.

## Como compilar, correr e testar

Requer Node 22+ (testado com 24).

```
cd authoring
npm install            # uma vez
npm run dev            # http://localhost:5180/  (watch + hot reload)
npm run check          # tsc --noEmit
npm test               # composição de shaders (fixtures em test/fixtures) e validação de todos os scene.json, presets incluídos
npm run build          # bundle único, sem servidor
node scripts/smoke.mjs        # com o servidor a correr: abre cada cena num Chromium headless (SwiftShader),
                              # verifica que compila e renderiza, guarda capturas em %TEMP%\santa-vibes-smoke
node scripts/smoke.mjs --gpu  # o mesmo com a GPU do PC (ANGLE/D3D11): fps e ms de GPU passam a ter significado
node scripts/smoke.mjs --gpu --quick --only aurora,halo --settle 10
                              # --quick salta hot reload e overlay; --only filtra cenas; --settle N segundos antes da captura
node scripts/smoke.mjs --gpu --quick --presets --settle 6
                              # --presets aplica também cada preset do scene.json e guarda <cena>@<preset>.png
```

O teste de fumo, por cena:

1. carrega a cena e confirma que compila e produz frames;
2. **hot reload**: acrescenta um comentário ao `scene.frag`, cronometra até a página recompilar e repõe o ficheiro (medido em 2026-10-04: 140–670 ms; critério do roadmap: < 1 s);
3. **overlay de erros**: injeta um erro de sintaxe, verifica que o overlay aponta para `scenes/<id>/scene.frag:<linha>` e que a cena anterior continua a correr, repõe o ficheiro e confirma a recuperação;
4. guarda `<id>.png` e `<id>-error.png` na pasta de saída (`--out`).

Sem `--gpu` usa SwiftShader (render por software), por isso os fps **não** dizem nada sobre desempenho. Com `--gpu` corre na GPU do PC, que também não é a da box: para desempenho real só a medição no passo 0.5.

## Estrutura

```
dev-server.mjs     servidor sem dependências + esbuild em watch
index.html         esqueleto da página (sem framework)
style.css
scripts/smoke.mjs  teste de fumo headless (DevTools Protocol, sem dependências)
test/              testes da composição; test/fixtures/compose-cases.json é o contrato partilhado com a tv-app
src/
  main.ts          arranque, painéis, hot reload, loop
  compose.ts       composição header + params + #include (referência para a tv-app)
  scene.ts         tipos e loaders de scene.json / perfis
  gl.ts            WebGL2: programas, FBOs, timer de GPU
  renderer.ts      passe de cena (ping-pong) + passe de saída, limite de fps, estatísticas
  signals.ts       sinais sintéticos (uBands / uMood / uBeat)
  ui.ts            widgets DOM e painel de parâmetros gerado do scene.json
  hot.ts           cliente SSE
```

## Hot reload

| Ficheiro alterado | O que acontece |
|---|---|
| `scenes/<atual>/scene.frag`, `shared/glsl/**` | recompila o shader; mantém o tempo e os buffers de feedback; erros aparecem em overlay |
| `scenes/<atual>/scene.json` | relê o JSON e regenera o painel; com um preset escolhido volta a aplicá-lo (para ver logo as edições ao preset), senão mantém os valores dos ids que existam |
| `shared/glsl/output.frag` | recompila o passe de saída |
| `shared/profiles/<atual>.json` | relê o perfil |
| pasta nova/removida em `scenes/` | atualiza a lista de cenas |
| `src/**/*.ts` | esbuild reconstrói e a página recarrega |
