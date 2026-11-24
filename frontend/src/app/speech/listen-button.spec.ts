import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ListenButton } from './listen-button';
import { SpeechService } from './speech.service';

/** A speech service that reads nothing: it says which text is being read, and counts the times it was stopped. */
class FakeSpeech {
  readonly key = signal<string | null>(null);
  readonly state = signal<'idle' | 'speaking' | 'paused'>('idle');
  stopped = 0;
  supported() {
    return true;
  }
  available() {
    return true;
  }
  play(key: string) {
    this.key.set(key);
    this.state.set('speaking');
  }
  stop() {
    this.stopped++;
    this.key.set(null);
    this.state.set('idle');
  }
}

describe('ListenButton', () => {
  let speech: FakeSpeech;

  beforeEach(() => {
    speech = new FakeSpeech();
    TestBed.configureTestingModule({ providers: [{ provide: SpeechService, useValue: speech }] });
  });

  function button(key: string) {
    const fixture = TestBed.createComponent(ListenButton);
    fixture.componentRef.setInput('key', key);
    fixture.componentRef.setInput('title', key);
    fixture.componentRef.setInput('paragraphs', ['…']);
    fixture.detectChanges();
    return fixture;
  }

  it('stops the story it was reading when the page moves on to another story', () => {
    const fixture = button('labours');
    speech.play('labours');
    fixture.componentRef.setInput('key', 'apotheosis'); // the "next" link: the same page, another story
    fixture.detectChanges();
    expect(speech.stopped).toBe(1);
    expect(speech.key()).toBeNull();
  });

  it('leaves alone what another speaker or the player is reading', () => {
    const fixture = button('labours');
    speech.play('all-the-stories');
    fixture.componentRef.setInput('key', 'apotheosis');
    fixture.detectChanges();
    fixture.destroy();
    expect(speech.stopped).toBe(0);
    expect(speech.key()).toBe('all-the-stories');
  });

  it('stops its reading when the page goes', () => {
    const fixture = button('labours');
    speech.play('labours');
    fixture.destroy();
    expect(speech.stopped).toBe(1);
  });
});
