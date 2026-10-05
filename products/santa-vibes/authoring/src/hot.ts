// Hot reload client: listens to the dev server's Server-Sent Events.

export interface BundleError {
  text: string;
  file: string;
  line: number;
}

export interface HotHandlers {
  onChange(path: string): void; // "scenes/<id>/scene.frag", "shared/glsl/lib/noise.glsl", ...
  onBundle(errors: BundleError[]): void;
  onStatus(connected: boolean): void;
}

export function connectHot(h: HotHandlers): void {
  const es = new EventSource('/events');
  es.addEventListener('open', () => h.onStatus(true));
  es.addEventListener('error', () => h.onStatus(false));
  es.addEventListener('message', (ev: MessageEvent<string>) => {
    let msg: { type?: string; path?: string; errors?: BundleError[] };
    try {
      msg = JSON.parse(ev.data) as typeof msg;
    } catch {
      return;
    }
    if (msg.type === 'change' && msg.path) h.onChange(msg.path);
    else if (msg.type === 'bundle') h.onBundle(msg.errors ?? []);
  });
}
