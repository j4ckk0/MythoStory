import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService, Character, Era, Story } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { CharacterChip } from '../shared/character-chip';

/** A row of the timeline: the stories of one generation of an era, or those the tradition does not date. */
interface Row {
  generation: number | null;
  stories: Story[];
}

/**
 * The mythic chronology: the eras in their order and, in each, the stories by generation before the Trojan War —
 * the oldest first —, then those the tradition does not date; within a generation, the cycles' order.
 */
@Component({
  selector: 'app-timeline',
  imports: [RouterLink, I18nPipe, I18nTextPipe, CharacterChip],
  templateUrl: './timeline.html',
  styleUrl: './timeline.css',
})
export class Timeline {
  private readonly catalog = inject(CatalogService);

  protected readonly eras = computed(() =>
    (this.catalog.catalog()?.eras ?? []).map((era: Era) => {
      const stories = this.catalog.storiesInCycleOrder().filter((s) => s.era === era.id);
      const generations = [...new Set(stories.map((s) => s.generation ?? null))].sort((a, b) =>
        a === null ? 1 : b === null ? -1 : b - a,
      );
      const dated = generations.some((g) => g !== null);
      const rows: Row[] = generations.map((generation) => ({ generation, stories: stories.filter((s) => (s.generation ?? null) === generation) }));
      return { era, rows, dated };
    }),
  );

  protected cycleOf(story: Story): string {
    return this.catalog.cycleOfStory().get(story.id)?.id ?? '';
  }

  protected cycleTitle(story: Story) {
    return this.catalog.cycleOfStory().get(story.id)?.title;
  }

  /** The story's three main characters. */
  protected characters(story: Story): Character[] {
    return story.characters.slice(0, 3).map((id) => this.catalog.characterById().get(id)).filter((c): c is Character => !!c);
  }

  /** The i18n key of a generation's label. */
  protected generationKey(generation: number): string {
    return generation === 0 ? 'timeline.generation.zero' : generation === 1 ? 'timeline.generation.one' : 'timeline.generation';
  }
}
