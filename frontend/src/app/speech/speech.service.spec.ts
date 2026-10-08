import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogService } from '../catalog/catalog';
import { ServerSpeechEngine } from './speech-engine';
import { group, split, SpeechService, VOICE_SAMPLES } from './speech.service';
import { VoiceSettings } from './voice-settings';

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

  it('reads a series, each cycle announced, and goes on from one story to the next', () => {
    speech.playAll('all', [
      { key: 'a', title: 'A', paragraphs: ['One. Two.'], intro: 'The cycle' },
      { key: 'b', title: 'B', paragraphs: ['Three.'] },
    ], 'en');
    const said = () => synthesis.spoken.map((s) => s.text);
    expect(said()).toEqual(['The cycle']);
    expect(speech.item()).toBe('a');
    expect(speech.paragraph()).toBe(-2);
    synthesis.spoken.at(-1)!.end(); // the intro
    synthesis.spoken.at(-1)!.end(); // A
    synthesis.spoken.at(-1)!.end(); // One.
    synthesis.spoken.at(-1)!.end(); // Two.
    expect(said().at(-1)).toBe('B');
    expect(speech.item()).toBe('b');
    expect(speech.itemIndex()).toBe(1);
  });

  it('skips to the next story, back to the previous one, and ignores the end of the sentence cut short', () => {
    speech.playAll('all', [
      { key: 'a', title: 'A', paragraphs: ['One. Two. Three.'] },
      { key: 'b', title: 'B', paragraphs: ['Four.'] },
    ], 'en');
    const cut = synthesis.spoken[0];
    speech.next();
    expect(synthesis.spoken.at(-1)!.text).toBe('B');
    cut.end(); // "A" ends after the jump: not followed
    expect(synthesis.spoken.at(-1)!.text).toBe('B');
    speech.previous(); // B has only just started: back to A
    expect(synthesis.spoken.at(-1)!.text).toBe('A');
    speech.next();
    speech.next(); // past the last story: the end
    expect(speech.state()).toBe('idle');
    expect(speech.item()).toBeNull();
  });

  it('lists the browser’s voices of a language, the best first, and reads with the one chosen', () => {
    synthesis.voices.push({ name: 'Amélie', lang: 'fr-CA', localService: true, default: false });
    synthesis.dispatchEvent(new Event('voiceschanged'));
    expect(speech.readerOf('fr')).toBe('browser');
    expect(speech.voices('fr').map((v) => v.name)).toEqual(['Thomas', 'Amélie']);
    TestBed.inject(VoiceSettings).choose('fr', 'Amélie');
    speech.play('k', 'Titre', [], 'fr');
    expect(synthesis.spoken.at(-1)?.voice).toBe('Amélie');
    speech.playSample('fr', 'Thomas'); // a sample: with its voice, whatever was chosen; no title
    expect(synthesis.spoken.at(-1)).toEqual(expect.objectContaining({ text: VOICE_SAMPLES.fr, voice: 'Thomas' }));
    expect(speech.key()).toBe('sample:Thomas');
    TestBed.inject(VoiceSettings).choose('fr', null);
  });

  it('groups sentences up to a length, for the voices that read several at once', () => {
    expect(group(['One.', 'Two.', 'Three.'], 10)).toEqual(['One. Two.', 'Three.']);
    expect(group(['A long sentence that does not fit.', 'Two.'], 10)).toEqual(['A long sentence that does not fit.', 'Two.']);
    expect(group(['One.', 'Two.'], undefined)).toEqual(['One.', 'Two.']);
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

const SIWIS = { id: 'fr-siwis', language: 'fr', name: 'Siwis', description: { en: 'Female voice', fr: 'Voix féminine' } };
const PIERRE = { id: 'fr-pierre', language: 'fr', name: 'Pierre' };

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
    http.expectOne('api/info').flush({ name: 'MythoStory', version: '0', platform: 'desktop', languages: ['en', 'fr'], voices: [SIWIS, PIERRE] });
    expect(TestBed.inject(CatalogService).info()?.voices?.map((v) => v.id)).toEqual(['fr-siwis', 'fr-pierre']);
    expect(speech.available('fr')).toBe(true); // the server's
    expect(speech.readerOf('fr')).toBe('server');
    expect(speech.readerOf('en')).toBe('browser');
    speech.play('k', 'Title', [], 'en');
    expect(spoken).toEqual(['browser: Title']); // no English voice on the server: the browser's
    delete (globalThis as Record<string, unknown>)['SpeechSynthesisUtterance'];
  });

  it('offers the server’s voices of a language, the default first, and reads with the one chosen', () => {
    localStorage.removeItem('mythostory.speech.voice.fr');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const speech = TestBed.inject(SpeechService);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('api/catalog').flush({ characters: [], stories: [], cycles: [], eras: [], places: [] });
    http.expectOne('api/info').flush({ name: 'MythoStory', version: '0', platform: 'desktop', languages: ['en', 'fr'], voices: [SIWIS, PIERRE] });
    expect(speech.voices('fr').map((v) => v.name)).toEqual(['Siwis', 'Pierre']);
    expect(speech.voiceOf('fr')).toBe('fr-siwis'); // nothing chosen: the default
    TestBed.inject(VoiceSettings).choose('fr', 'fr-pierre');
    expect(speech.voiceOf('fr')).toBe('fr-pierre');
    expect(localStorage.getItem('mythostory.speech.voice.fr')).toBe('fr-pierre'); // remembered
    TestBed.inject(VoiceSettings).choose('fr', 'fr-gone');
    expect(speech.voiceOf('fr')).toBe('fr-siwis'); // a voice the server no longer has: the default
    TestBed.inject(VoiceSettings).choose('fr', null);
    expect(localStorage.getItem('mythostory.speech.voice.fr')).toBeNull();
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
    const engine = new ServerSpeechEngine(() => [SIWIS, PIERRE], (url) => new FakeAudio(url) as unknown as HTMLAudioElement);
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

  it('names the voice chosen when it is one of the language’s, and only then', () => {
    FakeAudio.made = [];
    const engine = new ServerSpeechEngine(() => [SIWIS, PIERRE], (url) => new FakeAudio(url) as unknown as HTMLAudioElement);
    engine.speak('Un.', 'fr', () => undefined, 'fr-pierre');
    engine.speak('Un.', 'fr', () => undefined, 'en-alba');
    expect(FakeAudio.made.map((a) => a.url)).toEqual(['api/speech?lang=fr&voice=fr-pierre&text=Un.', 'api/speech?lang=fr&text=Un.']);
  });
});
