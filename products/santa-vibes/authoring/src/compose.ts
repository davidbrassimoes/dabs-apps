// Shader composition — the reference implementation of docs/FORMATO-CENA.md, section
// "Composição do shader". The tv-app (Kotlin) MUST produce byte-identical output for
// the same inputs. Rules:
//
//   1. shared/glsl/header.glsl, verbatim.
//   2. One line per declared param:  uniform float p_<id>;   (vec3 for "color")
//   3. The scene body, where each line matching  #include "<path>"  is replaced by the
//      content of shared/glsl/<path>, processed recursively with the same rule.
//      - A file is included at most once; later includes of the same file become a
//        single comment line, so line counting stays predictable.
//      - A missing include becomes  #error  so the compiler reports it in place.
//
// Every output line records where it came from (file + 1-based line) so compiler
// errors can be mapped back to the author's files.

import type { ParamDef } from './scene';

export interface LineOrigin {
  file: string; // e.g. "scenes/nebula/scene.frag" or "shared/glsl/lib/noise.glsl"
  line: number; // 1-based line in that file
}

export interface Composed {
  source: string;
  origins: LineOrigin[]; // origins[i] describes composed line i (0-based)
}

export interface MappedError {
  file: string;
  line: number;
  message: string;
}

export const INCLUDE_RE = /^\s*#include\s+"([^"]+)"\s*$/;

export function paramUniformDecl(p: ParamDef): string {
  return `uniform ${p.type === 'color' ? 'vec3' : 'float'} p_${p.id};`;
}

/** Scan a body (and, recursively, its includes) and fetch every referenced file once. */
export async function collectIncludes(
  body: string,
  fetchInclude: (path: string) => Promise<string>,
  into: Map<string, string | null> = new Map(),
): Promise<Map<string, string | null>> {
  for (const line of body.split('\n')) {
    const m = INCLUDE_RE.exec(line);
    if (!m || !m[1] || into.has(m[1])) continue;
    let text: string | null;
    try {
      text = await fetchInclude(m[1]);
    } catch {
      text = null;
    }
    into.set(m[1], text);
    if (text !== null) await collectIncludes(text, fetchInclude, into);
  }
  return into;
}

function splitLines(text: string): string[] {
  const lines = text.replace(/\r\n?/g, '\n').split('\n');
  if (lines.length > 1 && lines[lines.length - 1] === '') lines.pop();
  return lines;
}

export function compose(
  header: string,
  params: ParamDef[],
  body: string,
  bodyFile: string,
  includes: Map<string, string | null>,
): Composed {
  const out: string[] = [];
  const origins: LineOrigin[] = [];
  const included = new Set<string>();

  const push = (line: string, file: string, lineNo: number) => {
    out.push(line);
    origins.push({ file, line: lineNo });
  };

  const emitBody = (text: string, file: string) => {
    const lines = splitLines(text);
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i] ?? '';
      const m = INCLUDE_RE.exec(line);
      if (!m || !m[1]) {
        push(line, file, i + 1);
        continue;
      }
      const inc = m[1];
      if (included.has(inc)) {
        push(`// #include "${inc}" (already included)`, file, i + 1);
        continue;
      }
      included.add(inc);
      const text2 = includes.get(inc);
      if (text2 === undefined || text2 === null) {
        push(`#error include not found: "${inc}"`, file, i + 1);
        continue;
      }
      emitBody(text2, `shared/glsl/${inc}`);
    }
  };

  const headerLines = splitLines(header);
  for (let i = 0; i < headerLines.length; i++) push(headerLines[i] ?? '', 'shared/glsl/header.glsl', i + 1);

  for (let i = 0; i < params.length; i++) {
    const p = params[i];
    if (p) push(paramUniformDecl(p), '<params>', i + 1);
  }

  emitBody(body, bodyFile);

  return { source: out.join('\n') + '\n', origins };
}

/**
 * Map a GLSL info log to author files. Browsers emit "ERROR: 0:<line>: <message>";
 * ANGLE may also emit "WARNING: ...". Unknown lines are kept with file "".
 */
export function mapErrorLog(log: string, composed: Composed): MappedError[] {
  const out: MappedError[] = [];
  for (const raw of log.split('\n')) {
    const text = raw.trim();
    if (!text) continue;
    const m = /^(ERROR|WARNING):\s*\d+:(\d+):\s*(.*)$/.exec(text);
    if (!m) {
      out.push({ file: '', line: 0, message: text });
      continue;
    }
    const composedLine = Number(m[2]);
    const origin = composed.origins[composedLine - 1];
    out.push({
      file: origin?.file ?? '',
      line: origin?.line ?? composedLine,
      message: `${m[1] === 'WARNING' ? 'warning: ' : ''}${m[3] ?? ''}`,
    });
  }
  return out;
}
