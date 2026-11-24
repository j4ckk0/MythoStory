import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { en, I18nKey } from './en';
import { fr } from './fr';

/** The i18n languages: the backend's I18n.LANGUAGES, the first being the fallback. */
export const I18N_LANGUAGES = ['en', 'fr'] as const;
export type I18nLanguage = (typeof I18N_LANGUAGES)[number];

/** Each i18n language's name in itself, as the language list offers it: a reader finds their own whatever the page's. */
export const I18N_LANGUAGE_NAMES: Record<I18nLanguage, string> = { en: 'English', fr: 'Français' };

/** A catalog text in every i18n language, by code (core: I18nText). */
export type I18nText = Partial<Record<I18nLanguage, string>>;

const I18N_MESSAGES: Record<I18nLanguage, Record<I18nKey, string>> = { en, fr };
const I18N_STORAGE_KEY = 'mythostory.i18n.language';

/**
 * The application's i18n: the language the page speaks — the one chosen last, else the browser's (the phone's on
 * Android), else English — its messages, and the catalog's texts in it.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);
  readonly language = signal<I18nLanguage>(this.initialLanguage());
  /** The other language, which the settings list after the reader's. */
  readonly otherLanguage = computed<I18nLanguage>(() => (this.language() === 'en' ? 'fr' : 'en'));

  constructor() {
    this.document.documentElement.lang = this.language();
  }

  setLanguage(language: I18nLanguage): void {
    this.language.set(language);
    this.document.documentElement.lang = language;
    try {
      localStorage.setItem(I18N_STORAGE_KEY, language);
    } catch {
      // no storage (private window…): the choice lasts as long as the page
    }
  }

  /** A UI message, its {placeholders} replaced by the params. */
  message(key: I18nKey, params: Record<string, string | number> = {}): string {
    return (I18N_MESSAGES[this.language()][key] ?? key).replace(/\{(\w+)\}/g, (_, name: string) => String(params[name] ?? `{${name}}`));
  }

  /** A catalog text in the current language, else in the fallback language. */
  text(text: I18nText | undefined): string {
    if (!text) return '';
    return text[this.language()] ?? text[I18N_LANGUAGES[0]] ?? '';
  }

  private initialLanguage(): I18nLanguage {
    try {
      const stored = localStorage.getItem(I18N_STORAGE_KEY);
      if (isI18nLanguage(stored)) return stored;
    } catch {
      // no storage: the browser's language decides
    }
    const browser = (this.document.defaultView?.navigator.language ?? '').slice(0, 2);
    return isI18nLanguage(browser) ? browser : I18N_LANGUAGES[0];
  }
}

export function isI18nLanguage(value: unknown): value is I18nLanguage {
  return (I18N_LANGUAGES as readonly unknown[]).includes(value);
}
