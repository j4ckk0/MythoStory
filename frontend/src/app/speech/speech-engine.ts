import { I18nLanguage } from '../i18n/i18n.service';

/** A way to speak a text aloud: the browser's own, or the Android app's. */
export interface SpeechEngine {
  /** Whether it has a voice for the language — the browser's voices may come late: [ready] says when they are known. */
  available(language: I18nLanguage): boolean;
  /** Speaks the text; [onEnd] once it is said, not when it was cancelled. */
  speak(text: string, language: I18nLanguage, onEnd: () => void): void;
  /** Stops speaking at once, the current text and any queued. */
  cancel(): void;
  /** Prepares a text it will soon be asked to speak, so that no silence falls between two sentences. */
  prefetch?(text: string, language: I18nLanguage): void;
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
    /** The languages the server has a voice for: /api/info's voices, known once it has answered. */
    private readonly voices: () => readonly string[],
    private readonly createAudio: (url: string) => HTMLAudioElement = (url) => new Audio(url),
  ) {}

  available(language: I18nLanguage): boolean {
    return this.voices().includes(language);
  }

  speak(text: string, language: I18nLanguage, onEnd: () => void): void {
    const url = speechUrl(text, language);
    const audio = this.prefetched.get(url) ?? this.createAudio(url);
    this.prefetched.delete(url);
    this.playing = audio;
    const ended = () => {
      if (this.playing === audio) onEnd();
    };
    audio.onended = ended;
    // a sentence that cannot be said is skipped rather than stopping the reading
    audio.onerror = ended;
    void audio.play().catch(() => undefined);
  }

  cancel(): void {
    this.playing?.pause();
    this.playing = null;
    this.prefetched.clear();
  }

  prefetch(text: string, language: I18nLanguage): void {
    const url = speechUrl(text, language);
    if (this.prefetched.has(url)) return;
    const audio = this.createAudio(url);
    audio.preload = 'auto';
    audio.load();
    this.prefetched.set(url, audio);
  }
}

function speechUrl(text: string, language: I18nLanguage): string {
  return `api/speech?lang=${language}&text=${encodeURIComponent(text)}`;
}

/** The voice's locale for each i18n language: what the engines are asked for. */
const LOCALES: Record<I18nLanguage, string> = { en: 'en-GB', fr: 'fr-FR' };

/**
 * The browser's speech synthesis (Web Speech API), with the voices of the system: offline with local voices, and
 * those are preferred. The voice is the one of the language's locale (fr-FR, en-GB), else any of the language.
 */
export class WebSpeechEngine implements SpeechEngine {
  private voices: SpeechSynthesisVoice[] = [];

  constructor(
    private readonly synthesis: SpeechSynthesis,
    /** Told when the voices are known: browsers list them some time after the page has loaded. */
    onVoices: () => void,
  ) {
    const load = () => {
      this.voices = synthesis.getVoices();
      onVoices();
    };
    load();
    synthesis.addEventListener?.('voiceschanged', load);
  }

  available(language: I18nLanguage): boolean {
    return this.voice(language) !== undefined;
  }

  speak(text: string, language: I18nLanguage, onEnd: () => void): void {
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = LOCALES[language];
    utterance.voice = this.voice(language) ?? null;
    utterance.onend = onEnd;
    this.synthesis.speak(utterance);
  }

  cancel(): void {
    this.synthesis.cancel();
  }

  private voice(language: I18nLanguage): SpeechSynthesisVoice | undefined {
    const ofLanguage = this.voices.filter((v) => v.lang.toLowerCase().replace('_', '-').startsWith(language));
    const score = (v: SpeechSynthesisVoice) =>
      Number(v.lang.replace('_', '-') === LOCALES[language]) * 2 + Number(v.localService) + Number(v.default) * 0.5;
    return ofLanguage.sort((a, b) => score(b) - score(a))[0];
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
