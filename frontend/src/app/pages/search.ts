import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { normalize } from '../shared/text';

/** The characters shown at most: a query of one letter matches thousands. */
const SHOWN = 100;

/** The characters, groups and stories matching ?q=, in any i18n language, Greek and Roman names included, accents ignored. */
@Component({
  selector: 'app-search',
  imports: [RouterLink, I18nPipe, I18nTextPipe],
  templateUrl: './search.html',
})
export class Search {
  private readonly catalog = inject(CatalogService);
  /** The ?q= query parameter. */
  readonly q = input<string>('');

  protected readonly query = computed(() => normalize(this.q().trim()));

  /** Every character matching, those whose name starts with the query first. */
  private readonly matching = computed(() => {
    const query = this.query();
    if (!query) return [];
    return (this.catalog.catalog()?.characters ?? [])
      .map((c) => {
        const names = [...Object.values(c.name), ...Object.values(c.roman ?? {}), c.greek ?? ''].map((n) => normalize(n ?? ''));
        return { c, starts: names.some((n) => n.startsWith(query)), matches: names.some((n) => n.includes(query)) };
      })
      .filter((m) => m.matches)
      .sort((a, b) => Number(b.starts) - Number(a.starts))
      .map((m) => m.c);
  });

  protected readonly characters = computed(() => this.matching().slice(0, SHOWN));
  /** How many more match than are shown. */
  protected readonly more = computed(() => Math.max(0, this.matching().length - SHOWN));

  protected readonly groups = computed(() => {
    const query = this.query();
    if (!query) return [];
    return (this.catalog.catalog()?.groups ?? []).filter((g) => Object.values(g.name).some((name) => normalize(name ?? '').includes(query)));
  });

  protected readonly stories = computed(() => {
    const query = this.query();
    if (!query) return [];
    return this.catalog.storiesInCycleOrder().filter((s) => Object.values(s.title).some((title) => normalize(title ?? '').includes(query)));
  });
}
