import { ServerSpeechEngine, WebSpeechEngine } from './speech-engine';

/** An audio element that plays nothing: [play] answers as the test says, and the test fires its events. */
class FakeAudio {
  onended: (() => void) | null = null;
  onerror: (() => void) | null = null;
  preload = '';
  constructor(private readonly playing: () => Promise<void>) {}
  play() {
    return this.playing();
  }
  pause() {}
  load() {}
}

describe('ServerSpeechEngine', () => {
  const voices = () => [{ id: 'fr-siwis', language: 'fr', name: 'Siwis' }];

  it('goes on to the next text when the browser refuses to play one, rather than waiting for its end forever', async () => {
    const audio = new FakeAudio(() => Promise.reject(new DOMException('autoplay', 'NotAllowedError')));
    const engine = new ServerSpeechEngine(voices, () => audio as unknown as HTMLAudioElement);
    let ended = 0;
    engine.speak('Bonjour.', 'fr', () => ended++);
    await Promise.resolve();
    await Promise.resolve();
    expect(ended).toBe(1);
    audio.onerror?.(); // a failed load reported too: still one end, never a text skipped
    expect(ended).toBe(1);
  });

  it('ends each text once, though both its error and its end are reported', () => {
    const audio = new FakeAudio(() => Promise.resolve());
    const engine = new ServerSpeechEngine(voices, () => audio as unknown as HTMLAudioElement);
    let ended = 0;
    engine.speak('Bonjour.', 'fr', () => ended++);
    audio.onerror?.();
    audio.onended?.();
    expect(ended).toBe(1);
  });
});

describe('WebSpeechEngine', () => {
  interface FakeUtterance {
    text: string;
    onend: (() => void) | null;
    onerror: ((event: { error: string }) => void) | null;
  }
  let spoken: FakeUtterance[];

  beforeEach(() => {
    spoken = [];
    (globalThis as Record<string, unknown>)['SpeechSynthesisUtterance'] = class {
      lang = '';
      voice = null;
      onend = null;
      onerror = null;
      constructor(public text: string) {}
    };
  });

  afterEach(() => delete (globalThis as Record<string, unknown>)['SpeechSynthesisUtterance']);

  it('goes on when the synthesis fails on a text, as a browser does with an error and no end', () => {
    const synthesis = { getVoices: () => [], speak: (u: FakeUtterance) => spoken.push(u), cancel: () => undefined };
    const engine = new WebSpeechEngine(synthesis as unknown as SpeechSynthesis, () => undefined);
    let ended = 0;
    engine.speak('Bonjour.', 'fr', () => ended++);
    spoken[0].onerror?.({ error: 'synthesis-failed' });
    expect(ended).toBe(1);
    spoken[0].onend?.(); // some browsers end it as well: one end all the same
    expect(ended).toBe(1);
  });
});
