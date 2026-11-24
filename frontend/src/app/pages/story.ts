import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { I18nService } from '../i18n/i18n.service';
import { MapView } from '../map/map-view';
import { ListenButton } from '../speech/listen-button';
import { SpeechService } from '../speech/speech.service';
import { CharacterChip } from '../shared/character-chip';
import { GroupChip } from '../shared/group-chip';
import { Links } from '../shared/links';
import { Sources } from '../shared/sources';
import { paragraphs } from '../shared/text';
import { generationKey } from '../timeline/generation';

/** A story: its place in its cycles, its summary, its characters and groups, its ancient sources and links to read more. */
@Component({
  selector: 'app-story',
  imports: [RouterLink, I18nPipe, I18nTextPipe, CharacterChip, GroupChip, Links, Sources, MapView, ListenButton],
  templateUrl: './story.html',
})
export class StoryPage {
  private readonly catalog = inject(CatalogService);
  protected readonly i18n = inject(I18nService);
  protected readonly speech = inject(SpeechService);
  /** The :id route parameter. */
  readonly id = input.required<string>();

  protected readonly loaded = computed(() => this.catalog.catalog() !== null);
  protected readonly story = computed(() => this.catalog.storyById().get(this.id()));
  protected readonly paragraphs = computed(() => paragraphs(this.i18n.text(this.story()?.summary)));
  protected readonly characters = computed(() =>
    this.catalog.charactersOf(this.story()?.characters),
  );
  protected readonly groups = computed(() =>
    this.catalog.groupsOf(this.story()?.groups),
  );
  protected readonly cycles = computed(() => {
    const story = this.story();
    return story ? this.catalog.cyclePositions(story) : [];
  });
  protected readonly era = computed(() => this.catalog.eraById().get(this.story()?.era ?? ''));
  /** The story alone, for its inset map. */
  protected readonly mapped = computed(() => {
    const story = this.story();
    return story?.places?.length ? [story] : [];
  });

  protected readonly generationKey = generationKey;

  protected readonly related = computed(() =>
    this.catalog.storiesOf(this.story()?.related),
  );
}
