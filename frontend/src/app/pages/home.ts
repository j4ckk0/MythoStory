import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CHARACTER_KINDS, CatalogService } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';

/** The way in: the kinds of characters and the cycles of stories. */
@Component({
  selector: 'app-home',
  imports: [RouterLink, I18nPipe, I18nTextPipe],
  templateUrl: './home.html',
})
export class Home {
  protected readonly catalog = inject(CatalogService);

  protected readonly kinds = computed(() =>
    CHARACTER_KINDS.map((kind) => ({
      kind,
      count: (this.catalog.catalog()?.characters ?? []).filter((c) => c.kind === kind).length,
    })).filter((k) => k.count > 0),
  );
}
