import { Component, inject, input } from '@angular/core';
import { Source } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { I18N_LANGUAGES, I18nLanguage, I18nService } from '../i18n/i18n.service';

/**
 * Ancient sources — author, work, passage —, each with a link to a translation online: in the reader's language when
 * there is one, else in the other, marked with its language. A story's, or the lists of a group's members.
 */
@Component({
  selector: 'app-sources',
  imports: [I18nPipe, I18nTextPipe],
  template: `
    <ul class="sources">
      @for (source of sources(); track $index) {
        <li>
          <span class="author">{{ source.author | i18nText }}</span>, <cite>{{ source.work | i18nText }}</cite>
          @if (source.passage) {
            <span class="passage">{{ source.passage }}</span>
          }
          @if (reading(source); as read) {
            — <a [href]="read.url" target="_blank" rel="noopener">{{ 'story.read' | i18n }}</a>
            @if (read.lang !== i18n.language()) {
              <span class="lang" [title]="'i18n.name.' + read.lang | i18n">{{ read.lang }}</span>
            }
          }
        </li>
      }
    </ul>
  `,
})
export class Sources {
  protected readonly i18n = inject(I18nService);
  readonly sources = input<Source[]>([]);

  /** Where to read a source: in the reader's language when a translation is online, else in the other one. */
  protected reading(source: Source): { lang: I18nLanguage; url: string } | undefined {
    const language = this.i18n.language();
    for (const lang of [language, ...I18N_LANGUAGES.filter((l) => l !== language)]) {
      const url = source.url?.[lang];
      if (url) return { lang, url };
    }
    return undefined;
  }
}
