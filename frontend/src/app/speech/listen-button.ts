import { Component, OnDestroy, computed, effect, inject, input, untracked } from '@angular/core';
import { I18nPipe } from '../i18n/i18n.pipe';
import { I18nService } from '../i18n/i18n.service';
import { SpeechService } from './speech.service';

/**
 * The speaker: reads a text aloud — its title, then its paragraphs — in the reader's language; pause, resume, stop.
 * Hidden where nothing can speak, disabled where no voice speaks the language. Reading stops when the page goes,
 * or the language changes.
 */
@Component({
  selector: 'app-listen-button',
  imports: [I18nPipe],
  templateUrl: './listen-button.html',
  styleUrl: './listen-button.css',
})
export class ListenButton implements OnDestroy {
  protected readonly speech = inject(SpeechService);
  private readonly i18n = inject(I18nService);

  /** What is read: its key (a story's id), its title, its paragraphs. */
  readonly key = input.required<string>();
  readonly title = input.required<string>();
  readonly paragraphs = input.required<string[]>();

  protected readonly mine = computed(() => this.speech.key() === this.key());
  protected readonly state = computed(() => (this.mine() ? this.speech.state() : 'idle'));
  protected readonly available = computed(() => this.speech.available(this.i18n.language()));

  constructor() {
    // another language, another text: what was being read stops
    effect(() => {
      this.i18n.language();
      this.key();
      untracked(() => this.stopMine());
    });
  }

  protected play(): void {
    this.speech.play(this.key(), this.title(), this.paragraphs(), this.i18n.language());
  }

  protected toggle(): void {
    if (this.state() === 'speaking') this.speech.pause();
    else if (this.state() === 'paused') this.speech.resume();
    else this.play();
  }

  protected stop(): void {
    this.speech.stop();
  }

  ngOnDestroy(): void {
    this.stopMine();
  }

  private stopMine(): void {
    if (this.mine()) this.speech.stop();
  }
}
