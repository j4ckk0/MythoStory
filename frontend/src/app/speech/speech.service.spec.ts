import { TestBed } from '@angular/core/testing';
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
    TestBed.configureTestingModule({});
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
    TestBed.configureTestingModule({});
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
