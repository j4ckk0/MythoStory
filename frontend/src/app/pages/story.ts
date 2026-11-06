import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService, Character, Group, Story } from '../catalog/catalog';
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
    (this.story()?.characters ?? []).map((id) => this.catalog.characterById().get(id)).filter((c): c is Character => !!c),
  );
  protected readonly groups = computed(() =>
    (this.story()?.groups ?? []).map((id) => this.catalog.groupById().get(id)).filter((g): g is Group => !!g),
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

  /** The i18n key of a generation's label. */
  protected generationKey(generation: number): string {
    return generation === 0 ? 'timeline.generation.zero' : generation === 1 ? 'timeline.generation.one' : 'timeline.generation';
  }

  protected readonly related = computed(() =>
    (this.story()?.related ?? []).map((id) => this.catalog.storyById().get(id)).filter((s): s is Story => !!s),
  );
}
