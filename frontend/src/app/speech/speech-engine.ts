import { Voice } from '../catalog/catalog';
import { I18nLanguage, I18nText } from '../i18n/i18n.service';

/** A voice an engine offers for a language, which the settings let one choose: its id is the engine's own. */
export interface VoiceOption {
  id: string;
  name: string;
  description?: I18nText;
}

/** A way to speak a text aloud: the server's, the browser's own, or the Android app's. */
export interface SpeechEngine {
  /** Whether it has a voice for the language — the browser's voices may come late: [ready] says when they are known. */
  available(language: I18nLanguage): boolean;
  /** Its voices for the language, its default first; none when the system chooses (Android). */
  voices?(language: I18nLanguage): VoiceOption[];
  /**
   * Speaks the text, with the voice when it is one of the language's (its default else); [onEnd] once it is said,
   * not when it was cancelled.
   */
  speak(text: string, language: I18nLanguage, onEnd: () => void, voice?: string | null): void;
  /** Stops speaking at once, the current text and any queued. */
  cancel(): void;
  /** Prepares a text it will soon be asked to speak, so that no silence falls between two sentences. */
  prefetch?(text: string, language: I18nLanguage, voice?: string | null): void;
  /**
   * The longest text it reads well at once, sentences grouped up to it; one sentence at a time when absent — some
   * browsers cut long utterances short.
   */
  readonly groupUpTo?: number;
}

/**
 * The server's voices (backend: /api/speech, the desktop's Piper): neural, the same on every browser and system. Each
 * sentence is a WAV the page plays; the next one is fetched while the current one plays.
 */
export class ServerSpeechEngine implements SpeechEngine {
  /** Piper reads several sentences at once with a natural melody and pauses between them; the server takes 1000 characters. */
  readonly groupUpTo = 500;
  private playing: HTMLAudioElement | null = null;
  private readonly prefetched = new Map<string, HTMLAudioElement>();

  constructor(
    /** The server's voices: /api/info's, known once it has answered. */
    private readonly serverVoices: () => readonly Voice[],
    private readonly createAudio: (url: string) => HTMLAudioElement = (url) => new Audio(url),
  ) {}

  available(language: I18nLanguage): boolean {
    return this.voices(language).length > 0;
  }

  voices(language: I18nLanguage): VoiceOption[] {
    return this.serverVoices()
      .filter((v) => v.language === language)
      .map(({ id, name, description }) => ({ id, name, description }));
  }

  speak(text: string, language: I18nLanguage, onEnd: () => void, voice?: string | null): void {
    const url = this.url(text, language, voice);
    const audio = this.prefetched.get(url) ?? this.createAudio(url);
    this.prefetched.delete(url);
    this.playing = audio;
    const ended = once(() => {
      if (this.playing === audio) onEnd();
    });
    audio.onended = ended;
    // a text that cannot be said — it failed to load, or the browser refused to play it — is skipped rather than
    // leaving the reading waiting for an end that never comes
    audio.onerror = ended;
    void audio.play().catch(ended);
  }

  cancel(): void {
    this.playing?.pause();
    this.playing = null;
    this.prefetched.clear();
  }

  prefetch(text: string, language: I18nLanguage, voice?: string | null): void {
    const url = this.url(text, language, voice);
    if (this.prefetched.has(url)) return;
    const audio = this.createAudio(url);
    audio.preload = 'auto';
    audio.load();
    this.prefetched.set(url, audio);
  }

  /** The sentence's address: the voice named when it is one of the language's — the server's default else. */
  private url(text: string, language: I18nLanguage, voice?: string | null): string {
    const known = voice && this.voices(language).some((v) => v.id === voice);
    return `api/speech?lang=${language}${known ? `&voice=${encodeURIComponent(voice)}` : ''}&text=${encodeURIComponent(text)}`;
  }
}

/** A callback that runs the first time it is called, and never again. */
function once(callback: () => void): () => void {
  let done = false;
  return () => {
    if (done) return;
    done = true;
    callback();
  };
}

/** The voice's locale for each i18n language: what the engines are asked for. */
const LOCALES: Record<I18nLanguage, string> = { en: 'en-GB', fr: 'fr-FR' };

/**
 * The browser's speech synthesis (Web Speech API), with the voices of the system: offline with local voices, and
 * those are preferred. The voice is the one of the language's locale (fr-FR, en-GB), else any of the language.
 */
export class WebSpeechEngine implements SpeechEngine {
  private systemVoices: SpeechSynthesisVoice[] = [];
  /** The text being said, kept: an utterance nothing refers to may be collected before its end is told (Chrome). */
  private speaking: SpeechSynthesisUtterance | null = null;

  constructor(
    private readonly synthesis: SpeechSynthesis,
    /** Told when the voices are known: browsers list them some time after the page has loaded. */
    onVoices: () => void,
  ) {
    const load = () => {
      this.systemVoices = synthesis.getVoices();
      onVoices();
    };
    load();
    synthesis.addEventListener?.('voiceschanged', load);
  }

  available(language: I18nLanguage): boolean {
    return this.ofLanguage(language).length > 0;
  }

  voices(language: I18nLanguage): VoiceOption[] {
    return this.ofLanguage(language).map((v) => ({
      id: v.voiceURI || v.name,
      name: v.name,
      description: { en: v.lang + (v.localService ? '' : ' · online'), fr: v.lang + (v.localService ? '' : ' · en ligne') },
    }));
  }

  speak(text: string, language: I18nLanguage, onEnd: () => void, voice?: string | null): void {
    const utterance = new SpeechSynthesisUtterance(text);
    const voices = this.ofLanguage(language);
    utterance.lang = LOCALES[language];
    utterance.voice = voices.find((v) => (v.voiceURI || v.name) === voice) ?? voices[0] ?? null;
    // a failure ends the text too (Chrome reports one with an error and no end), and once only: some browsers report
    // both, which would skip the next text
    const ended = once(onEnd);
    utterance.onend = ended;
    utterance.onerror = ended;
    this.speaking = utterance;
    this.synthesis.speak(utterance);
  }

  cancel(): void {
    this.speaking = null;
    this.synthesis.cancel();
  }

  /** The voices of the language, the best first: the locale's, local, the system's default. */
  private ofLanguage(language: I18nLanguage): SpeechSynthesisVoice[] {
    const ofLanguage = this.systemVoices.filter((v) => v.lang.toLowerCase().replace('_', '-').startsWith(language));
    const score = (v: SpeechSynthesisVoice) =>
      Number(v.lang.replace('_', '-') === LOCALES[language]) * 2 + Number(v.localService) + Number(v.default) * 0.5;
    return ofLanguage.sort((a, b) => score(b) - score(a));
  }
}

/**
 * The Android app's speech: its WebView has no speech synthesis, so the app lends the page its own text-to-speech
 * through a bridge (android: SpeechBridge.kt), window.MythoStorySpeech; it tells when its engine is ready by calling
 * window.mythoStorySpeechReady, and the end of each text by calling window.mythoStorySpeechDone with its id.
 */
export interface AndroidSpeechBridge {
  available(locale: string): boolean;
  speak(id: string, text: string, locale: string): void;
  stop(): void;
}

export class AndroidSpeechEngine implements SpeechEngine {
  private next = 0;
  private readonly waiting = new Map<string, () => void>();

  constructor(
    private readonly bridge: AndroidSpeechBridge,
    host: { mythoStorySpeechDone?: (id: string) => void; mythoStorySpeechReady?: () => void },
    /** Told when the engine is ready, which it is some time after the app has started. */
    onReady: () => void,
  ) {
    host.mythoStorySpeechReady = onReady;
    host.mythoStorySpeechDone = (id) => {
      const onEnd = this.waiting.get(id);
      this.waiting.delete(id);
      onEnd?.();
    };
  }

  available(language: I18nLanguage): boolean {
    return this.bridge.available(LOCALES[language]);
  }

  speak(text: string, language: I18nLanguage, onEnd: () => void): void {
    const id = String(this.next++);
    this.waiting.set(id, onEnd);
    this.bridge.speak(id, text, LOCALES[language]);
  }

  cancel(): void {
    this.waiting.clear();
    this.bridge.stop();
  }
}
