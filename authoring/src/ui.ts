// Tiny DOM widget factories (no framework). Everything the panel shows is built from
// these, including the params panel generated from scene.json.

import { hexToRgb, rgbToHex, type ParamDef, type ParamValue, type ParamValues, type SceneJson } from './scene';

export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  cls?: string,
  text?: string,
): HTMLElementTagNameMap[K] {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined) e.textContent = text;
  return e;
}

export function section(title: string, container: HTMLElement): HTMLElement {
  container.replaceChildren();
  const h = el('h2', undefined, title);
  container.appendChild(h);
  return container;
}

export function group(parent: HTMLElement, title: string): HTMLElement {
  const g = el('div', 'group');
  g.appendChild(el('h3', undefined, title));
  parent.appendChild(g);
  return g;
}

export function row(parent: HTMLElement): HTMLElement {
  const r = el('div', 'row');
  parent.appendChild(r);
  return r;
}

export interface SliderOpts {
  label: string;
  min: number;
  max: number;
  step: number;
  value: number;
  format?: (v: number) => string;
  onInput: (v: number) => void;
}

export interface Handle<T> {
  root: HTMLElement;
  set(v: T): void;
  setDisabled(d: boolean): void;
}

export function slider(parent: HTMLElement, o: SliderOpts): Handle<number> {
  const root = el('label', 'ctl ctl-slider');
  const name = el('span', 'name', o.label);
  const input = el('input');
  input.type = 'range';
  input.min = String(o.min);
  input.max = String(o.max);
  input.step = String(o.step);
  input.value = String(o.value);
  const out = el('span', 'val');
  const fmt = o.format ?? ((v: number) => formatNumber(v, o.step));
  out.textContent = fmt(o.value);
  input.addEventListener('input', () => {
    const v = Number(input.value);
    out.textContent = fmt(v);
    o.onInput(v);
  });
  root.append(name, input, out);
  parent.appendChild(root);
  return {
    root,
    set(v) {
      input.value = String(v);
      out.textContent = fmt(v);
    },
    setDisabled(d) {
      input.disabled = d;
      root.classList.toggle('disabled', d);
    },
  };
}

export function checkbox(parent: HTMLElement, label: string, checked: boolean, onChange: (v: boolean) => void): Handle<boolean> {
  const root = el('label', 'ctl ctl-check');
  const input = el('input');
  input.type = 'checkbox';
  input.checked = checked;
  input.addEventListener('change', () => onChange(input.checked));
  root.append(input, el('span', 'name', label));
  parent.appendChild(root);
  return {
    root,
    set(v) {
      input.checked = v;
    },
    setDisabled(d) {
      input.disabled = d;
    },
  };
}

export function colorInput(parent: HTMLElement, label: string, hex: string, onInput: (hex: string) => void): Handle<string> {
  const root = el('label', 'ctl ctl-color');
  const input = el('input');
  input.type = 'color';
  input.value = hex;
  const out = el('span', 'val', hex);
  input.addEventListener('input', () => {
    out.textContent = input.value;
    onInput(input.value);
  });
  root.append(el('span', 'name', label), input, out);
  parent.appendChild(root);
  return {
    root,
    set(v) {
      input.value = v;
      out.textContent = v;
    },
    setDisabled(d) {
      input.disabled = d;
    },
  };
}

export interface Option {
  value: string;
  label: string;
  group?: string; // optional <optgroup> label; options with the same group are listed together
}

export interface SelectHandle extends Handle<string> {
  setOptions(opts: Option[], value?: string): void;
  value(): string;
}

export function select(parent: HTMLElement, label: string, opts: Option[], value: string, onChange: (v: string) => void): SelectHandle {
  const root = el('label', 'ctl ctl-select');
  const sel = el('select');
  const fill = (list: Option[], v?: string) => {
    sel.replaceChildren();
    const groups = new Map<string, HTMLElement>();
    for (const o of list) {
      const opt = el('option', undefined, o.label);
      opt.value = o.value;
      if (o.group) {
        let g = groups.get(o.group);
        if (!g) {
          g = el('optgroup');
          (g as HTMLOptGroupElement).label = o.group;
          groups.set(o.group, g);
          sel.appendChild(g);
        }
        g.appendChild(opt);
      } else {
        sel.appendChild(opt);
      }
    }
    if (v !== undefined) sel.value = v;
  };
  fill(opts, value);
  sel.addEventListener('change', () => onChange(sel.value));
  root.append(el('span', 'name', label), sel);
  parent.appendChild(root);
  return {
    root,
    set(v) {
      sel.value = v;
    },
    setDisabled(d) {
      sel.disabled = d;
    },
    setOptions: fill,
    value: () => sel.value,
  };
}

export function button(parent: HTMLElement, label: string, onClick: () => void): HTMLButtonElement {
  const b = el('button', 'btn', label);
  b.type = 'button';
  b.addEventListener('click', onClick);
  parent.appendChild(b);
  return b;
}

export function formatNumber(v: number, step: number): string {
  const decimals = step >= 1 ? 0 : Math.min(4, Math.max(0, Math.ceil(-Math.log10(step))));
  return v.toFixed(decimals);
}

// ---------------------------------------------------------------- params panel

export interface ParamsPanel {
  /** Push a value into the UI (e.g. after reset) without firing onChange. */
  set(id: string, value: ParamValue): void;
}

export function buildParamsPanel(
  container: HTMLElement,
  scene: SceneJson,
  values: ParamValues,
  onChange: (id: string, value: ParamValue) => void,
): ParamsPanel {
  container.replaceChildren();
  const handles = new Map<string, (v: ParamValue) => void>();
  if (scene.params.length === 0) {
    container.appendChild(el('p', 'muted', 'Esta cena não tem parâmetros.'));
    return { set: () => undefined };
  }

  const groups = new Map<string, HTMLElement>();
  const groupFor = (name: string | undefined): HTMLElement => {
    if (!name) return container;
    let g = groups.get(name);
    if (!g) {
      g = group(container, name);
      groups.set(name, g);
    }
    return g;
  };

  // Ungrouped params first, then groups in order of appearance (keeps the panel tidy).
  const ordered = [...scene.params.filter((p) => !p.group), ...scene.params.filter((p) => p.group)];

  for (const p of ordered) {
    const parent = groupFor(p.group);
    const current = values.get(p.id);
    const label = p.drift && p.drift > 0 ? `${p.label} ~` : p.label;
    switch (p.type) {
      case 'color': {
        const hex = Array.isArray(current) ? rgbToHex(current) : String(p.default);
        const h = colorInput(parent, label, hex, (v) => onChange(p.id, hexToRgb(v)));
        handles.set(p.id, (v) => {
          if (Array.isArray(v)) h.set(rgbToHex(v));
        });
        break;
      }
      case 'bool': {
        const h = checkbox(parent, label, current === 1, (v) => onChange(p.id, v ? 1 : 0));
        handles.set(p.id, (v) => h.set(v === 1));
        break;
      }
      default: {
        const step = p.step ?? (p.type === 'int' ? 1 : defaultStep(p));
        const h = slider(parent, {
          label,
          min: p.min ?? 0,
          max: p.max ?? 1,
          step,
          value: typeof current === 'number' ? current : Number(p.default),
          onInput: (v) => onChange(p.id, p.type === 'int' ? Math.round(v) : v),
        });
        handles.set(p.id, (v) => {
          if (typeof v === 'number') h.set(v);
        });
      }
    }
  }

  return {
    set(id, value) {
      handles.get(id)?.(value);
    },
  };
}

function defaultStep(p: ParamDef): number {
  const range = (p.max ?? 1) - (p.min ?? 0);
  if (range <= 0) return 0.001;
  const raw = range / 200;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  return mag;
}
