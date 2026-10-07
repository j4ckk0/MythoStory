import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CatalogService } from '../catalog/catalog';
import { I18nLanguage } from '../i18n/i18n.service';
import { AndroidSpeechBridge, AndroidSpeechEngine, ServerSpeechEngine, SpeechEngine, WebSpeechEngine } from './speech-engine';

/** One sentence to say, and the paragraph it belongs to (-1: the title). */
interface Sentence {
  text: string;
  paragraph: number;
}

export type SpeechState = 'idle' | 'speaking' | 'paused';

/**
 * Reads texts aloud — a story's title and summary —, sentence after sentence: long utterances get cut short by some
 * browsers, and pausing then resumes at the sentence where it stopped. One reading at a time, known by its key.
 *
 * The voice, for each language: the Android app's text-to-speech in the app; else the server's (the desktop's Piper),
 * the same everywhere and natural; else the browser's own, which some systems lack or make robotic.
 */
@Injectable({ providedIn: 'root' })
export class SpeechService {
  /** The device's own engine: the Android app's, or the browser's; null when it has none. */
  private readonly device: SpeechEngine | null;
  private readonly server: ServerSpeechEngine;
  private readonly android: boolean;
  /** The engine of the reading in progress. */
  private engine: SpeechEngine | null = null;
  /** Bumped when the browser's voices become known, so that availability is asked again. */
  private readonly voices = signal(0);

  readonly state = signal<SpeechState>('idle');
  /** The key of the text being read (or paused): a story's id. */
  readonly key = signal<string | null>(null);
  /** The paragraph being read, to highlight it; -1 for the title, null when nothing is. */
  readonly paragraph = signal<number | null>(null);

  private sentences: Sentence[] = [];
  private position = 0;
  private language: I18nLanguage = 'en';

  constructor() {
    const window = inject(DOCUMENT).defaultView as (Window & { MythoStorySpeech?: AndroidSpeechBridge }) | null;
    const voicesKnown = () => this.voices.update((n) => n + 1);
    const catalog = inject(CatalogService);
    this.server = new ServerSpeechEngine(() => catalog.info()?.voices ?? []);
    this.android = !!window?.MythoStorySpeech;
    if (window?.MythoStorySpeech) this.device = new AndroidSpeechEngine(window.MythoStorySpeech, window as never, voicesKnown);
    else if (window?.speechSynthesis && typeof SpeechSynthesisUtterance !== 'undefined')
      this.device = new WebSpeechEngine(window.speechSynthesis, voicesKnown);
    else this.device = null;
  }

  /** The engine that speaks the language: see the class. */
  private engineFor(language: I18nLanguage): SpeechEngine | null {
    this.voices();
    if (this.android) return this.device;
    if (this.server.available(language)) return this.server;
    return this.device?.available(language) ? this.device : null;
  }

  /** Whether this device can speak at all: no engine, no button. */
  readonly supported = computed(() => this.device !== null || this.server.available('en') || this.server.available('fr'));

  /** Whether there is a voice for the language. */
  available(language: I18nLanguage): boolean {
    return this.engineFor(language)?.available(language) ?? false;
  }

  /** Reads the title, then the paragraphs, from the start. */
  play(key: string, title: string, paragraphs: string[], language: I18nLanguage): void {
    this.engine?.cancel();
    this.engine = this.engineFor(language);
    if (!this.engine) return;
    this.key.set(key);
    this.language = language;
    this.sentences = [{ text: title, paragraph: -1 }, ...paragraphs.flatMap((p, i) => split(p, language).map((text) => ({ text, paragraph: i })))];
    this.position = 0;
    this.speakFromPosition();
  }

  pause(): void {
    if (this.state() !== 'speaking') return;
    this.state.set('paused');
    this.engine?.cancel();
  }

  resume(): void {
    if (this.state() === 'paused') this.speakFromPosition();
  }

  stop(): void {
    this.engine?.cancel();
    this.state.set('idle');
    this.key.set(null);
    this.paragraph.set(null);
  }

  private speakFromPosition(): void {
    const sentence = this.sentences[this.position];
    if (!sentence || !this.engine) {
      this.stop();
      return;
    }
    this.state.set('speaking');
    this.paragraph.set(sentence.paragraph);
    const key = this.key();
    const next = this.sentences[this.position + 1];
    if (next) this.engine.prefetch?.(next.text, this.language);
    this.engine.speak(sentence.text, this.language, () => {
      // a sentence that ends after a pause, a stop or another reading has started is not followed
      if (this.state() !== 'speaking' || this.key() !== key) return;
      this.position++;
      this.speakFromPosition();
    });
  }
}

/** A paragraph's sentences, as the language cuts them. */
export function split(paragraph: string, language: I18nLanguage): string[] {
  if (typeof Intl !== 'undefined' && 'Segmenter' in Intl) {
    const segmenter = new Intl.Segmenter(language, { granularity: 'sentence' });
    return [...segmenter.segment(paragraph)].map((s) => s.segment.trim()).filter(Boolean);
  }
  return paragraph.split(/(?<=[.!?…])\s+/).map((s) => s.trim()).filter(Boolean);
}
