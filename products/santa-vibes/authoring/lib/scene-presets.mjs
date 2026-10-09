// Insert a preset into a scene.json text without reformatting the rest of the file (preset lab,
// decision 16). The new preset goes last in `presets`, written in the style of the files already
// in scenes/ (one block per preset, two-space indents). The result is parsed back and must equal
// the original plus the preset, or the whole file is rewritten with JSON.stringify instead.

/** Returns the new file text. Throws when `text` is not a JSON object or the id is taken. */
export function insertPreset(text, preset) {
  const json = JSON.parse(text);
  if (!json || typeof json !== 'object' || Array.isArray(json)) throw new Error('scene.json is not an object');
  if ((json.presets ?? []).some((p) => p.id === preset.id)) throw new Error(`preset "${preset.id}" already exists`);
  const expected = { ...json, presets: [...(json.presets ?? []), preset] };
  const eol = text.includes('\r\n') ? '\r\n' : '\n';
  const block = formatPreset(preset, '    ', eol);

  let out = null;
  const m = /"presets"\s*:\s*\[/.exec(text);
  if (m) {
    const close = matchingBracket(text, m.index + m[0].length - 1);
    if (close > 0) {
      const inner = text.slice(m.index + m[0].length, close);
      const before = text.slice(0, close).replace(/\s*$/, '');
      out = inner.trim() === ''
        ? `${text.slice(0, m.index + m[0].length)}${eol}${block}${eol}  ${text.slice(close)}`
        : `${before},${eol}${block}${eol}  ${text.slice(close)}`;
    }
  } else {
    const end = text.lastIndexOf('}');
    const before = text.slice(0, end).replace(/\s*$/, '');
    out = `${before},${eol}  "presets": [${eol}${block}${eol}  ]${eol}${text.slice(end)}`;
  }
  try {
    if (out && JSON.stringify(JSON.parse(out)) === JSON.stringify(expected)) return out;
  } catch {
    /* fall through */
  }
  return JSON.stringify(expected, null, 2) + eol;
}

function formatPreset(p, indent, eol) {
  const lines = [`${indent}{`, `${indent}  "id": ${JSON.stringify(p.id)},`, `${indent}  "name": ${JSON.stringify(p.name)},`];
  const params = Object.entries(p.params ?? {}).map(([k, v]) => `${JSON.stringify(k)}: ${JSON.stringify(v)}`).join(', ');
  lines.push(`${indent}  "params": { ${params} }${p.palette ? ',' : ''}`);
  if (p.palette) lines.push(`${indent}  "palette": [${p.palette.map((c) => JSON.stringify(c)).join(', ')}]`);
  lines.push(`${indent}}`);
  return lines.join(eol);
}

/** Index of the `]` closing the `[` at `open`, skipping strings; -1 if none. */
function matchingBracket(text, open) {
  let depth = 0;
  for (let i = open; i < text.length; i++) {
    const c = text[i];
    if (c === '"') {
      for (i++; i < text.length && text[i] !== '"'; i++) if (text[i] === '\\') i++;
    } else if (c === '[' || c === '{') depth++;
    else if (c === ']' || c === '}') {
      depth--;
      if (depth === 0) return c === ']' ? i : -1;
    }
  }
  return -1;
}
