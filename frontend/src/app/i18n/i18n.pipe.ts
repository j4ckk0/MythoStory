import { Pipe, PipeTransform, inject } from '@angular/core';
import { I18nKey } from './en';
import { I18nService, I18nText } from './i18n.service';

/**
 * A UI message in the current language: {{ 'nav.home' | i18n }}, {{ 'home.counts' | i18n: { characters: 99 } }}.
 * Impure, so that switching the language redraws it.
 */
@Pipe({ name: 'i18n', pure: false })
export class I18nPipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  /** The key is a string for the templates' computed keys ('kind.' + kind); an unknown one shows as itself. */
  transform(key: I18nKey | string, params?: Record<string, string | number>): string {
    return this.i18n.message(key as I18nKey, params);
  }
}

/** A catalog text in the current language: {{ character.name | i18nText }}. Impure, like the i18n pipe. */
@Pipe({ name: 'i18nText', pure: false })
export class I18nTextPipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(text: I18nText | undefined): string {
    return this.i18n.text(text);
  }
}
