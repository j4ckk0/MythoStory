import { Injectable } from '@angular/core';

/**
 * How long the page waits before opening its session again, once the socket closed without the page going away: a
 * second at first, well within the server's grace, then longer each time, up to half a minute, should the server be
 * gone for good.
 */
const RECONNECT_FIRST_MS = 1000;
const RECONNECT_MAX_MS = 30_000;

/**
 * The page's session with the server (backend: /api/session, PageSessions): a WebSocket the page keeps open while it
 * lives, whose first message names the session; as the page goes away, it closes the socket and sends a beacon — the
 * desktop server stops once its last page is closed. A socket rather than a stream of server-sent events: browsers
 * allow six HTTP connections per host, all tabs together, and a stream held by each tab would leave the seventh tab
 * without one; sockets are not counted among them. Should the browser crash, the socket closes, which the server
 * notices too; should it close while the page lives (a server restarted), the page opens it again, within the
 * server's grace. The Android app's server ignores it all.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private socket: WebSocket | null = null;
  private id: string | null = null;
  private reconnect: ReturnType<typeof setTimeout> | null = null;
  private reconnectDelay = RECONNECT_FIRST_MS;

  constructor() {
    // a page back from the browser's history cache opens a new session
    if (typeof addEventListener !== 'undefined') {
      addEventListener('pageshow', (event) => {
        if ((event as PageTransitionEvent).persisted) this.open();
      });
    }
  }

  open(): void {
    if (this.socket || this.reconnect || typeof WebSocket === 'undefined') return;
    addEventListener('pagehide', this.close);
    this.connect();
  }

  private connect(): void {
    this.reconnect = null;
    const url = new URL('api/session', document.baseURI);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(url);
    this.socket = socket;
    socket.onopen = () => (this.reconnectDelay = RECONNECT_FIRST_MS);
    socket.onmessage = (event) => (this.id = event.data as string);
    socket.onclose = () => {
      // closed by the server or the network, not by the page going away (close() forgets the socket first)
      if (this.socket !== socket) return;
      this.socket = null;
      this.id = null;
      this.reconnect = setTimeout(() => this.connect(), this.reconnectDelay);
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, RECONNECT_MAX_MS);
    };
  }

  private readonly close = () => {
    if (this.id) navigator.sendBeacon(`api/session/${this.id}/closed`);
    const socket = this.socket;
    this.socket = null;
    this.id = null;
    socket?.close();
    if (this.reconnect) clearTimeout(this.reconnect);
    this.reconnect = null;
    removeEventListener('pagehide', this.close);
  };
}
