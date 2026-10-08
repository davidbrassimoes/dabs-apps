// Real signals from the PC (ROADMAP 1.6): the pc-bridge serves its `SIGN` packets on a local
// WebSocket (ws://127.0.0.1:47810, binary frames). Packets go into a SignalMixer exactly like
// the TV's, so a scene reacts here as it will on the TV. Reconnects every 2 s while on.

import { parseSign } from './sign.ts';
import { SignalMixer } from './mixer.ts';

export const BRIDGE_URL = 'ws://127.0.0.1:47810';

export type BridgeState = 'off' | 'connecting' | 'open' | 'closed';

export class PcBridge {
  readonly mixer = new SignalMixer();
  state: BridgeState = 'off';
  /** Packets in the last full second. */
  packetRate = 0;

  private ws: WebSocket | null = null;
  private retry: ReturnType<typeof setTimeout> | null = null;
  private count = 0;
  private rateTimer: ReturnType<typeof setInterval> | null = null;

  constructor(private readonly url = BRIDGE_URL) {
    this.mixer.reactivity = 0.7; // the TV's default
  }

  start(): void {
    if (this.state !== 'off') return;
    this.state = 'connecting';
    this.rateTimer = setInterval(() => {
      this.packetRate = this.count;
      this.count = 0;
    }, 1000);
    this.open();
  }

  stop(): void {
    this.state = 'off';
    if (this.retry) clearTimeout(this.retry);
    if (this.rateTimer) clearInterval(this.rateTimer);
    this.retry = this.rateTimer = null;
    this.ws?.close();
    this.ws = null;
    this.packetRate = 0;
  }

  private open(): void {
    const ws = new WebSocket(this.url);
    ws.binaryType = 'arraybuffer';
    ws.onopen = () => (this.state = 'open');
    ws.onmessage = (e) => {
      const p = e.data instanceof ArrayBuffer ? parseSign(e.data) : null;
      if (p) {
        this.mixer.onPacket(p);
        this.count++;
      }
    };
    ws.onclose = () => {
      if (this.state === 'off') return;
      this.state = 'closed';
      this.retry = setTimeout(() => {
        if (this.state !== 'off') this.open();
      }, 2000);
    };
    this.ws = ws;
  }
}
