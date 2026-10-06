import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';

/** Every story, cycle by cycle, in each cycle's order. */
@Component({
  selector: 'app-stories',
  imports: [RouterLink, I18nPipe, I18nTextPipe],
  template: `
    <h1>{{ 'stories.title' | i18n }}</h1>
    @for (cycle of catalog.catalog()?.cycles ?? []; track cycle.id) {
      <section class="cycle" [id]="cycle.id">
        <h2>{{ cycle.title | i18nText }}</h2>
        <p class="muted">{{ cycle.description | i18nText }}</p>
        <ol>
          @for (id of cycle.stories; track id) {
            @if (catalog.storyById().get(id); as story) {
              <li><a [routerLink]="['/stories', story.id]">{{ story.title | i18nText }}</a></li>
            }
          }
        </ol>
      </section>
    }
  `,
})
export class Stories {
  protected readonly catalog = inject(CatalogService);
}
