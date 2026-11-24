import { Component, computed, effect, inject, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { I18nService } from '../i18n/i18n.service';
import { SpeechService } from './speech.service';

/**
 * The player of a series being read aloud — all the stories, or a cycle's —, at the bottom of every page, so that one
 * can listen while browsing: what is being read (its story, a link to it), where in the series, and the controls.
 * A single story's reading stays with its own speaker button. The reading stops when the language changes.
 */
@Component({
  selector: 'app-player',
  imports: [RouterLink, I18nPipe, I18nTextPipe],
  templateUrl: './player.html',
  styleUrl: './player.css',
})
export class Player {
  protected readonly speech = inject(SpeechService);
  private readonly catalog = inject(CatalogService);
  private readonly i18n = inject(I18nService);

  /** A series is being read: more than one text. */
  protected readonly shown = computed(() => this.speech.state() !== 'idle' && this.speech.items().length > 1);
  protected readonly story = computed(() => this.catalog.storyById().get(this.speech.item() ?? ''));
  /** The cycle being read, when the series is one cycle's; undefined for all the stories. */
  protected readonly cycle = computed(() => {
    const key = this.speech.key() ?? '';
    return key.startsWith('cycle:') ? this.catalog.catalog()?.cycles.find((c) => c.id === key.slice(6)) : undefined;
  });

  constructor() {
    effect(() => {
      const language = this.i18n.language();
      untracked(() => {
        if (this.shown() && language !== this.speech.language()) this.speech.stop();
      });
    });
  }
}
