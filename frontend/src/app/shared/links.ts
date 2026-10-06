import { Component, computed, inject, input } from '@angular/core';
import { LINK_KINDS, Link, LinkKind } from '../catalog/catalog';
import { I18nPipe } from '../i18n/i18n.pipe';
import { I18nService } from '../i18n/i18n.service';

/**
 * Links grouped by kind — Wikipedia, articles, videos —, those in the reader's language first, the others marked with
 * their language. They open outside the page (on Android, in the browser).
 */
@Component({
  selector: 'app-links',
  imports: [I18nPipe],
  template: `
    @for (group of groups(); track group.kind) {
      <section class="group">
        <h3>{{ 'links.' + group.kind | i18n }}</h3>
        <ul>
          @for (link of group.links; track link.url) {
            <li>
              <a [href]="link.url" target="_blank" rel="noopener">{{ link.title }}</a>
              @if (link.lang !== i18n.language()) {
                <span class="lang" [title]="'i18n.name.' + link.lang | i18n">{{ link.lang }}</span>
              }
            </li>
          }
        </ul>
      </section>
    }
  `,
  styleUrl: './links.css',
})
export class Links {
  protected readonly i18n = inject(I18nService);
  readonly links = input<Link[]>([]);

  protected readonly groups = computed(() => {
    const language = this.i18n.language();
    return LINK_KINDS.map((kind: LinkKind) => ({
      kind: kind as LinkKind,
      links: this.links()
        .filter((link) => link.kind === kind)
        .sort((a, b) => Number(b.lang === language) - Number(a.lang === language)),
    })).filter((group) => group.links.length > 0);
  });
}
