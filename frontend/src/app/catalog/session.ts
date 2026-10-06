import { Injectable } from '@angular/core';

/**
 * The page's session with the server (backend: /api/session, PageSessions): a stream the page keeps open while it
 * lives, whose first event names the session; as the page goes away, a beacon closes it — the desktop server stops
 * once its last page is closed. Browsers keep such streams open even in background tabs, which slow their timers
 * down; and should the browser crash, the stream breaks, which the server notices too. The Android app's server
 * ignores it all.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private stream: EventSource | null = null;
  private id: string | null = null;

  open(): void {
    // no EventSource in the tests' jsdom
    if (this.stream || typeof EventSource === 'undefined') return;
    this.stream = new EventSource('api/session');
    this.stream.addEventListener('session', (event) => (this.id = (event as MessageEvent<string>).data));
    addEventListener('pagehide', this.close);
  }

  constructor() {
    // a page back from the browser's history cache opens a new session
    if (typeof addEventListener !== 'undefined') {
      addEventListener('pageshow', (event) => {
        if ((event as PageTransitionEvent).persisted) this.open();
      });
    }
  }

  private readonly close = () => {
    if (this.id) navigator.sendBeacon(`api/session/${this.id}/closed`);
    this.stream?.close();
    this.stream = null;
    this.id = null;
    removeEventListener('pagehide', this.close);
  };
}
