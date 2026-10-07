import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { ListenAll } from '../speech/listen-all';
import { SpeechService } from '../speech/speech.service';

/** Every story, cycle by cycle, in each cycle's order; all of them, or a cycle's, read aloud on demand. */
@Component({
  selector: 'app-stories',
  imports: [RouterLink, I18nPipe, I18nTextPipe, ListenAll],
  template: `
    <h1>{{ 'stories.title' | i18n }}</h1>
    <app-listen-all />
    @for (cycle of catalog.catalog()?.cycles ?? []; track cycle.id) {
      <section class="cycle" [id]="cycle.id">
        <h2>{{ cycle.title | i18nText }}</h2>
        <p class="muted">{{ cycle.description | i18nText }}</p>
        <app-listen-all [cycle]="cycle" />
        <ol>
          @for (id of cycle.stories; track id) {
            @if (catalog.storyById().get(id); as story) {
              <li [class.reading-item]="speech.item() === story.id"><a [routerLink]="['/stories', story.id]">{{ story.title | i18nText }}</a></li>
            }
          }
        </ol>
      </section>
    }
  `,
})
export class Stories {
  protected readonly catalog = inject(CatalogService);
  protected readonly speech = inject(SpeechService);
}
