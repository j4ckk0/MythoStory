import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogService } from '../catalog/catalog';
import { ServerSpeechEngine } from './speech-engine';
import { split, SpeechService } from './speech.service';

/** A speech synthesis that says nothing: it records the utterances, and ends them when the test says. */
class FakeSynthesis extends EventTarget {
  spoken: { text: string; lang: string; voice: string | null; end: () => void }[] = [];
  cancelled = 0;
  voices = [
    { name: 'Thomas', lang: 'fr-FR', localService: true, default: false },
    { name: 'Daniel', lang: 'en-GB', localService: false, default: true },
    { name: 'Alex', lang: 'en-US', localService: true, default: false },
  ];
  getVoices() {
    return this.voices;
  }
  speak(u: { text: string; lang: string; voice: { name: string } | null; onend: () => void }) {
    this.spoken.push({ text: u.text, lang: u.lang, voice: u.voice?.name ?? null, end: () => u.onend() });
  }
  cancel() {
    this.cancelled++;
  }
}

describe('SpeechService', () => {
  let synthesis: FakeSynthesis;
  let speech: SpeechService;

  beforeEach(() => {
    synthesis = new FakeSynthesis();
    Object.assign(window, { speechSynthesis: synthesis });
    (globalThis as Record<string, unknown>)['SpeechSynthesisUtterance'] = class {
      lang = '';
      voice = null;
      onend: () => void = () => undefined;
      constructor(public text: string) {}
    };
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    speech = TestBed.inject(SpeechService);
  });

  afterEach(() => {
    delete (window as unknown as Record<string, unknown>)['speechSynthesis'];
    delete (globalThis as Record<string, unknown>)['SpeechSynthesisUtterance'];
  });

  it('reads the title, then each sentence, with a voice of the language, the locale’s first', () => {
    expect(speech.available('fr')).toBe(true);
    speech.play('labours', 'Les travaux', ['Héraclès part. Il revient.', 'Il devient dieu.'], 'fr');
    expect(synthesis.spoken.map((s) => [s.text, s.voice])).toEqual([['Les travaux', 'Thomas']]);
    expect(speech.paragraph()).toBe(-1);
    synthesis.spoken[0].end();
    synthesis.spoken[1].end();
    expect(synthesis.spoken.map((s) => s.text)).toEqual(['Les travaux', 'Héraclès part.', 'Il revient.']);
    expect(speech.paragraph()).toBe(0);
    synthesis.spoken[2].end();
    synthesis.spoken[3].end();
    expect(speech.state()).toBe('idle');
    expect(speech.key()).toBeNull();
  });

  it('prefers the locale, en-GB, over a local voice of another English', () => {
    speech.play('k', 'Title', [], 'en');
    expect(synthesis.spoken[0].voice).toBe('Daniel');
    expect(synthesis.spoken[0].lang).toBe('en-GB');
  });

  it('resumes at the sentence where it paused, and ignores the end of the one cut short', () => {
    speech.play('k', 'Title', ['One. Two. Three.'], 'en');
    synthesis.spoken[0].end();
    speech.pause();
    expect(speech.state()).toBe('paused');
    synthesis.spoken[1].end(); // the browser ends the cancelled utterance: not followed
    expect(synthesis.spoken.length).toBe(2);
    speech.resume();
    expect(synthesis.spoken.map((s) => s.text)).toEqual(['Title', 'One.', 'One.']);
  });

  it('has no voice for a language the device does not speak', () => {
    synthesis.voices = synthesis.voices.filter((v) => !v.lang.startsWith('fr'));
    synthesis.dispatchEvent(new Event('voiceschanged'));
    expect(speech.available('fr')).toBe(false);
    expect(speech.available('en')).toBe(true);
  });

  it('cuts paragraphs into sentences', () => {
    expect(split('Il vint. Il vit ! Il vainquit. Et après ?', 'fr')).toEqual(['Il vint.', 'Il vit !', 'Il vainquit.', 'Et après ?']);
  });
});

describe('SpeechService on Android', () => {
  afterEach(() => {
    for (const name of ['MythoStorySpeech', 'mythoStorySpeechDone', 'mythoStorySpeechReady']) delete (window as unknown as Record<string, unknown>)[name];
  });

  it('speaks through the app’s bridge, and goes on when the app says a text is done', () => {
    const spoken: [string, string, string][] = [];
    let ready = false;
    const bridge = {
      available: (locale: string) => ready && locale === 'fr-FR',
      speak: (id: string, text: string, locale: string) => spoken.push([id, text, locale]),
      stop: () => undefined,
    };
    Object.assign(window, { MythoStorySpeech: bridge });
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const speech = TestBed.inject(SpeechService);
    const host = window as unknown as { mythoStorySpeechReady: () => void; mythoStorySpeechDone: (id: string) => void };

    expect(speech.supported()).toBe(true);
    expect(speech.available('fr')).toBe(false); // the engine is not ready yet
    ready = true;
    host.mythoStorySpeechReady();
    expect(speech.available('fr')).toBe(true);
    expect(speech.available('en')).toBe(false);

    speech.play('k', 'Titre', ['Une phrase.'], 'fr');
    host.mythoStorySpeechDone(spoken[0][0]);
    expect(spoken.map(([, text, locale]) => [text, locale])).toEqual([['Titre', 'fr-FR'], ['Une phrase.', 'fr-FR']]);
  });
});

describe('SpeechService with the server’s voices', () => {
  afterEach(() => {
    delete (window as unknown as Record<string, unknown>)['speechSynthesis'];
  });

  it('prefers the server’s voice for a language it has, the browser’s for the others', () => {
    const spoken: string[] = [];
    Object.assign(window, {
      speechSynthesis: Object.assign(new EventTarget(), {
        getVoices: () => [{ name: 'Daniel', lang: 'en-GB', localService: true, default: true }],
        speak: (u: { text: string }) => spoken.push('browser: ' + u.text),
        cancel: () => undefined,
      }),
    });
    (globalThis as Record<string, unknown>)['SpeechSynthesisUtterance'] = class {
      constructor(public text: string) {}
    };
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const speech = TestBed.inject(SpeechService);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('api/catalog').flush({ characters: [], stories: [], cycles: [], eras: [], places: [] });
    http.expectOne('api/info').flush({ name: 'MythoStory', version: '0', platform: 'desktop', languages: ['en', 'fr'], voices: ['fr'] });
    expect(TestBed.inject(CatalogService).info()?.voices).toEqual(['fr']);
    expect(speech.available('fr')).toBe(true); // the server's
    speech.play('k', 'Title', [], 'en');
    expect(spoken).toEqual(['browser: Title']); // no English voice on the server: the browser's
    delete (globalThis as Record<string, unknown>)['SpeechSynthesisUtterance'];
  });
});

describe('ServerSpeechEngine', () => {
  /** An audio element that plays nothing: it records what it was given, and ends when the test says. */
  class FakeAudio {
    static made: FakeAudio[] = [];
    preload = '';
    played = false;
    paused = false;
    loaded = false;
    onended: (() => void) | null = null;
    onerror: (() => void) | null = null;
    constructor(public url: string) {
      FakeAudio.made.push(this);
    }
    play() {
      this.played = true;
      return Promise.resolve();
    }
    pause() {
      this.paused = true;
    }
    load() {
      this.loaded = true;
    }
  }

  it('plays each sentence the server says, the next one fetched while the current one plays', () => {
    FakeAudio.made = [];
    const engine = new ServerSpeechEngine(() => ['fr'], (url) => new FakeAudio(url) as unknown as HTMLAudioElement);
    expect(engine.available('fr')).toBe(true);
    expect(engine.available('en')).toBe(false);
    let ended = 0;
    engine.prefetch('Deux.', 'fr');
    engine.speak('Un.', 'fr', () => ended++);
    engine.speak('Deux.', 'fr', () => ended++);
    expect(FakeAudio.made.map((a) => a.url)).toEqual(['api/speech?lang=fr&text=Deux.', 'api/speech?lang=fr&text=Un.']);
    expect(FakeAudio.made[0].loaded).toBe(true); // prefetched, then played: not fetched twice
    expect(FakeAudio.made[0].played).toBe(true);
    FakeAudio.made[1].onended?.(); // "Un." ends after "Deux." began: not counted
    FakeAudio.made[0].onended?.();
    expect(ended).toBe(1);
    engine.cancel();
    expect(FakeAudio.made[0].paused).toBe(true);
  });
});
