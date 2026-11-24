import { Component, computed, effect, inject, input, untracked } from '@angular/core';
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
export class ListenButton {
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
    // another language, another text — the "next" link keeps this page and gives it another story —, or the page
    // gone: the text this speaker was reading stops. The text it was reading, not the one it now shows: hence the
    // key kept for the cleanup, which runs before each change and as the page goes.
    effect((onCleanup) => {
      this.i18n.language();
      const key = this.key();
      onCleanup(() => untracked(() => {
        if (this.speech.key() === key) this.speech.stop();
      }));
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

}
