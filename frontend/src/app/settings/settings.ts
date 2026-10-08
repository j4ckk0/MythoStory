import { Component, computed, inject } from '@angular/core';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { I18nLanguage, I18nService } from '../i18n/i18n.service';
import { SpeechService } from '../speech/speech.service';
import { VoiceSettings } from '../speech/voice-settings';

/** The settings: the voice each language is read aloud with, each one heard before it is chosen. */
@Component({
  selector: 'app-settings',
  imports: [I18nPipe, I18nTextPipe],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class Settings {
  protected readonly speech = inject(SpeechService);
  private readonly settings = inject(VoiceSettings);
  private readonly i18n = inject(I18nService);
  /** The page's language first. */
  protected readonly languages = computed(() => [this.i18n.language(), this.i18n.otherLanguage()]);
  protected readonly legends: Record<I18nLanguage, string> = { fr: 'settings.voices.fr', en: 'settings.voices.en' };

  /** The default voice is remembered as none: a later default (a better voice shipped) is then the one heard. */
  protected choose(language: I18nLanguage, voice: string, isDefault: boolean): void {
    this.settings.choose(language, isDefault ? null : voice);
  }

  protected playing(voice: string): boolean {
    return this.speech.key() === `sample:${voice}` && this.speech.state() !== 'idle';
  }

  protected sample(language: I18nLanguage, voice: string): void {
    if (this.playing(voice)) this.speech.stop();
    else this.speech.playSample(language, voice);
  }
}
