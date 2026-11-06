import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService, Character, Group, Story } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';

import { FamilyTree } from '../family/family-tree';
import { CharacterChip } from '../shared/character-chip';
import { GroupChip } from '../shared/group-chip';
import { Links } from '../shared/links';

/** A character: who they are, their family and groups, the stories they appear in, and links to read more. */
@Component({
  selector: 'app-character',
  imports: [RouterLink, I18nPipe, I18nTextPipe, CharacterChip, GroupChip, Links, FamilyTree],
  templateUrl: './character.html',
})
export class CharacterPage {
  private readonly catalog = inject(CatalogService);
  /** The :id route parameter. */
  readonly id = input.required<string>();

  protected readonly loaded = computed(() => this.catalog.catalog() !== null);
  protected readonly character = computed(() => this.catalog.characterById().get(this.id()));
  protected readonly parents = computed(() =>
    (this.character()?.parents ?? []).map((id) => this.catalog.characterById().get(id)).filter((c): c is Character => !!c),
  );
  protected readonly childCount = computed(() => this.catalog.childrenByCharacter().get(this.id())?.length ?? 0);
  protected readonly groups = computed(() => this.catalog.groupsByCharacter().get(this.id()) ?? []);
  /**
   * The stories they appear in: by name, then through their groups — Castor sails in the voyage of the Argo as an
   * Argonaut —, each story once, with the group it came through.
   */
  protected readonly stories = computed(() => {
    const named = this.catalog.storiesByCharacter().get(this.id()) ?? [];
    const seen = new Set(named.map((s) => s.id));
    const stories: { story: Story; group?: Group }[] = named.map((story) => ({ story }));
    for (const group of this.groups()) {
      for (const story of this.catalog.storiesByGroup().get(group.id) ?? []) {
        if (!seen.has(story.id)) {
          seen.add(story.id);
          stories.push({ story, group });
        }
      }
    }
    return stories;
  });
}
