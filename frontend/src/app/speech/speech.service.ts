import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CatalogService } from '../catalog/catalog';
import { I18nLanguage } from '../i18n/i18n.service';
import { AndroidSpeechBridge, AndroidSpeechEngine, ServerSpeechEngine, SpeechEngine, VoiceOption, WebSpeechEngine } from './speech-engine';
import { VoiceSettings } from './voice-settings';

/** A text to read: its key (a story's id), its title, its paragraphs, and what to announce before it (a cycle's title). */
export interface Reading {
  key: string;
  title: string;
  paragraphs: string[];
  intro?: string;
}

/** One stretch to say (a sentence, or sentences the engine groups), the reading it belongs to ([item], its index in [items]) and its paragraph (-1: the title, -2: the intro). */
interface Sentence {
  text: string;
  paragraph: number;
  item: number;
}

export type SpeechState = 'idle' | 'speaking' | 'paused';

/** Who reads a language aloud: the server (Piper), the browser, the Android app's text-to-speech, or no one. */
export type SpeechReader = 'server' | 'browser' | 'android' | null;

/** What a voice says when the settings let one hear it: the Iliad's first line, in the voice's language. */
export const VOICE_SAMPLES: Record<I18nLanguage, string> = {
  en: 'Sing, goddess, the anger of Peleus’ son Achilles, that brought countless ills upon the Achaeans.',
  fr: 'Chante, déesse, la colère d’Achille, fils de Pélée, qui causa aux Achéens des maux sans nombre.',
};

/**
 * Reads texts aloud — a story's title and summary, or a whole series of them —, sentence after sentence: long
 * utterances get cut short by some browsers, and pausing then resumes at the sentence where it stopped. One reading
 * at a time, known by its key: a story's id, or a series' ("all", "cycle:theogony").
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
  /** Bumped when the device's voices become known (the browser's list, or the Android engine ready), so that availability is asked again. */
  private readonly voicesChanged = signal(0);

  readonly state = signal<SpeechState>('idle');
  /** The key of the reading in progress (or paused): a story's id, or a series'. */
  readonly key = signal<string | null>(null);
  /** The texts of the reading, in their order. */
  readonly items = signal<readonly Reading[]>([]);
  /** The index, in [items], of the text being read. */
  readonly itemIndex = signal(0);
  /** The key of the text being read: the story to highlight. */
  readonly item = computed(() => (this.state() === 'idle' ? null : (this.items()[this.itemIndex()]?.key ?? null)));
  /** The paragraph being read, to highlight it; -1 for the title, -2 for the intro, null when nothing is. */
  readonly paragraph = signal<number | null>(null);
  /** The language the reading is in: the player and the speaker buttons stop the reading when the page's language changes. */
  readonly language = signal<I18nLanguage>('en');

  private readonly settings = inject(VoiceSettings);
  /** The voice of the reading in progress when it was given one (a sample); the settings' else. */
  private voice: string | null = null;
  private sentences: Sentence[] = [];
  private position = 0;
  /** Bumped at every sentence spoken, pause and stop: an engine's late "said" for an earlier one is ignored. */
  private turn = 0;

  constructor() {
    const window = inject(DOCUMENT).defaultView as (Window & { MythoStorySpeech?: AndroidSpeechBridge }) | null;
    const voicesKnown = () => this.voicesChanged.update((n) => n + 1);
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
    this.voicesChanged();
    if (this.android) return this.device;
    if (this.server.available(language)) return this.server;
    return this.device?.available(language) ? this.device : null;
  }

  /** Whether this device can speak at all: no engine, no button. */
  readonly supported = computed(() => this.device !== null || this.server.available('en') || this.server.available('fr'));

  /** Who reads the language aloud: the settings say which voices one can choose from. */
  readerOf(language: I18nLanguage): SpeechReader {
    const engine = this.engineFor(language);
    if (!engine) return null;
    return engine === this.server ? 'server' : this.android ? 'android' : 'browser';
  }

  /** The voices one can choose for the language, the default first: none when the system chooses (Android). */
  voices(language: I18nLanguage): VoiceOption[] {
    return this.engineFor(language)?.voices?.(language) ?? [];
  }

  /** The voice the language is read with: the one chosen when the engine has it, its default else. */
  voiceOf(language: I18nLanguage): string | null {
    const voices = this.voices(language);
    const chosen = this.settings.voice(language);
    return voices.find((v) => v.id === chosen)?.id ?? voices[0]?.id ?? null;
  }

  /** A voice's sample said aloud (the settings); its key "sample:<voice>" tells which is playing. */
  playSample(language: I18nLanguage, voice: string): void {
    this.playAll(`sample:${voice}`, [{ key: `sample:${voice}`, title: '', paragraphs: [VOICE_SAMPLES[language]] }], language, voice);
  }

  /** Whether there is a voice for the language. */
  available(language: I18nLanguage): boolean {
    return this.engineFor(language)?.available(language) ?? false;
  }

  /** Reads the title, then the paragraphs, from the start. */
  play(key: string, title: string, paragraphs: string[], language: I18nLanguage): void {
    this.playAll(key, [{ key, title, paragraphs }], language);
  }

  /**
   * Reads the texts one after the other, each one's intro, title and paragraphs, from the first: with the voice,
   * else the one the settings chose for the language.
   */
  playAll(key: string, items: Reading[], language: I18nLanguage, voice?: string): void {
    this.engine?.cancel();
    this.engine = this.engineFor(language);
    if (!this.engine || !items.length) return;
    this.voice = voice ?? null;
    this.key.set(key);
    this.items.set(items);
    this.language.set(language);
    this.sentences = items.flatMap((reading, item) => [
      ...(reading.intro ? [{ text: reading.intro, paragraph: -2, item }] : []),
      ...(reading.title ? [{ text: reading.title, paragraph: -1, item }] : []),
      ...reading.paragraphs.flatMap((p, i) => group(split(p, language), this.engine!.groupUpTo).map((text) => ({ text, paragraph: i, item }))),
    ]);
    this.position = 0;
    this.speakFromPosition();
  }

  pause(): void {
    if (this.state() !== 'speaking') return;
    this.turn++;
    this.state.set('paused');
    this.engine?.cancel();
  }

  resume(): void {
    if (this.state() === 'paused') this.speakFromPosition();
  }

  /** On to the next text of the reading, from its start; the end of the reading after the last. */
  next(): void {
    const item = this.sentences[this.position]?.item ?? 0;
    this.jumpTo(this.sentences.findIndex((s) => s.item > item));
  }

  /** Back to the start of the text being read — of the one before, when it has only just started. */
  previous(): void {
    const item = this.sentences[this.position]?.item ?? 0;
    const start = this.sentences.findIndex((s) => s.item === item);
    const target = this.position - start > 2 || item === 0 ? item : item - 1;
    this.jumpTo(this.sentences.findIndex((s) => s.item === target));
  }

  stop(): void {
    this.turn++;
    this.engine?.cancel();
    this.state.set('idle');
    this.key.set(null);
    this.items.set([]);
    this.paragraph.set(null);
  }

  private jumpTo(position: number): void {
    if (this.state() === 'idle') return;
    this.turn++;
    this.engine?.cancel();
    if (position < 0) {
      this.stop();
      return;
    }
    this.position = position;
    this.speakFromPosition();
  }

  private speakFromPosition(): void {
    const sentence = this.sentences[this.position];
    if (!sentence || !this.engine) {
      this.stop();
      return;
    }
    const turn = ++this.turn;
    this.state.set('speaking');
    this.itemIndex.set(sentence.item);
    this.paragraph.set(sentence.paragraph);
    // the voice chosen is asked at every sentence: a choice made while reading is heard from the next one
    const voice = this.voice ?? this.settings.voice(this.language());
    const next = this.sentences[this.position + 1];
    if (next) this.engine.prefetch?.(next.text, this.language(), voice);
    this.engine.speak(
      sentence.text,
      this.language(),
      () => {
        // the end of a sentence said before a pause, a jump, a stop or another reading is not followed
        if (turn !== this.turn) return;
        this.position++;
        this.speakFromPosition();
      },
      voice,
    );
  }
}

/** Consecutive sentences joined while they fit in [limit] characters; each one alone when there is no limit. */
export function group(sentences: string[], limit: number | undefined): string[] {
  if (!limit) return sentences;
  const groups: string[] = [];
  for (const sentence of sentences) {
    const last = groups.length - 1;
    if (last >= 0 && groups[last].length + 1 + sentence.length <= limit) groups[last] += ' ' + sentence;
    else groups.push(sentence);
  }
  return groups;
}

/** A paragraph's sentences, as the language cuts them. */
export function split(paragraph: string, language: I18nLanguage): string[] {
  if (typeof Intl !== 'undefined' && 'Segmenter' in Intl) {
    const segmenter = new Intl.Segmenter(language, { granularity: 'sentence' });
    return [...segmenter.segment(paragraph)].map((s) => s.segment.trim()).filter(Boolean);
  }
  return paragraph.split(/(?<=[.!?…])\s+/).map((s) => s.trim()).filter(Boolean);
}
