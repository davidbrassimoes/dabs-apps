# authoring — ferramenta de autoria (Fase 0.2)

Página WebGL2 **só para desenvolvimento**: iterar cenas no PC com hot reload. Não vai para a box.

Requisitos (ver `docs/ROADMAP.md`, passo 0.2):
- Lê `../scenes/*` e `../shared/*` diretamente — nunca copia nem edita cópias.
- Composição de shaders igual à da `tv-app` (`docs/FORMATO-CENA.md`).
- Painel de parâmetros gerado do `scene.json`; seletor de cena e de perfil; sliders de sinais sintéticos.
- Suporte a `feedback`, `renderScale`, passe de saída (`shared/glsl/output.frag`), contador de fps.
- Dependências mínimas (servidor de dev leve, TypeScript, sem framework de UI).

> Por implementar.
