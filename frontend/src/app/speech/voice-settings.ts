import { Injectable, signal } from '@angular/core';
import { I18N_LANGUAGES, I18nLanguage } from '../i18n/i18n.service';

const STORAGE_KEY = 'mythostory.speech.voice.';

/**
 * The voice chosen for each i18n language (the settings page), remembered by the browser: an engine's voice id, or
 * null for the engine's default. A voice the engine no longer has reads with its default.
 */
@Injectable({ providedIn: 'root' })
export class VoiceSettings {
  private readonly chosen = signal<Record<I18nLanguage, string | null>>(
    Object.fromEntries(I18N_LANGUAGES.map((l) => [l, read(l)])) as Record<I18nLanguage, string | null>,
  );

  voice(language: I18nLanguage): string | null {
    return this.chosen()[language];
  }

  choose(language: I18nLanguage, voice: string | null): void {
    this.chosen.update((chosen) => ({ ...chosen, [language]: voice }));
    try {
      if (voice) localStorage.setItem(STORAGE_KEY + language, voice);
      else localStorage.removeItem(STORAGE_KEY + language);
    } catch {
      // no storage (private window…): the choice lasts as long as the page
    }
  }
}

function read(language: I18nLanguage): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY + language);
  } catch {
    return null;
  }
}
