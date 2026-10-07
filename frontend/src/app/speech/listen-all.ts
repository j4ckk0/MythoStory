import { Component, computed, inject, input } from '@angular/core';
import { CatalogService, Cycle, Story } from '../catalog/catalog';
import { I18nPipe } from '../i18n/i18n.pipe';
import { I18nService } from '../i18n/i18n.service';
import { paragraphs } from '../shared/text';
import { Reading, SpeechService } from './speech.service';

/**
 * Reads a series of stories aloud, one after the other: every cycle's, or one cycle's ([cycle]). Each cycle's title is
 * announced as its first story begins. The player at the bottom of the page then takes over.
 */
@Component({
  selector: 'app-listen-all',
  imports: [I18nPipe],
  template: `
    @if (speech.supported()) {
      <span class="listen">
      <button type="button" class="listen-all" [class.active]="reading()" [disabled]="!available()" (click)="play()"
              [title]="(available() ? label() : 'speech.unavailable') | i18n">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M3 9v6h4l5 5V4L7 9H3z" />
          <path d="M16.5 12A4.5 4.5 0 0 0 14 8v8a4.5 4.5 0 0 0 2.5-4zM14 3.2v2.1a7 7 0 0 1 0 13.4v2.1a9 9 0 0 0 0-17.6z" />
        </svg>
        {{ label() | i18n: { count: count() } }}
      </button>
      @if (reading()) {
        <button type="button" class="stop" (click)="speech.stop()">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6h12v12H6z" /></svg>
          {{ 'speech.stopReading' | i18n }}
        </button>
      }
      </span>
    }
  `,
  styleUrl: './listen-button.css',
})
export class ListenAll {
  protected readonly speech = inject(SpeechService);
  private readonly catalog = inject(CatalogService);
  private readonly i18n = inject(I18nService);
  /** The cycle to read; every cycle when absent. */
  readonly cycle = input<Cycle | undefined>();

  private readonly key = computed(() => (this.cycle() ? `cycle:${this.cycle()!.id}` : 'all'));
  protected readonly label = computed(() => (this.cycle() ? 'speech.listenCycle' : 'speech.listenAll'));
  protected readonly available = computed(() => this.speech.available(this.i18n.language()));
  protected readonly reading = computed(() => this.speech.key() === this.key());

  /** The stories, cycle by cycle, each cycle's title announced before its first story. */
  private readonly readings = computed<Reading[]>(() => {
    const cycles = this.cycle() ? [this.cycle()!] : (this.catalog.catalog()?.cycles ?? []);
    const seen = new Set<string>();
    return cycles.flatMap((cycle) =>
      cycle.stories
        .filter((id) => !seen.has(id) && seen.add(id))
        .map((id) => this.catalog.storyById().get(id))
        .filter((s): s is Story => !!s)
        .map((story, i) => ({
          key: story.id,
          title: this.i18n.text(story.title),
          paragraphs: paragraphs(this.i18n.text(story.summary)),
          intro: i === 0 ? this.i18n.text(cycle.title) : undefined,
        })),
    );
  });

  protected readonly count = computed(() => this.readings().length);

  protected play(): void {
    this.speech.playAll(this.key(), this.readings(), this.i18n.language());
  }
}
