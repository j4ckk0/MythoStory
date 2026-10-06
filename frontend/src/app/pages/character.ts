import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService, Character } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';

import { FamilyTree } from '../family/family-tree';
import { CharacterChip } from '../shared/character-chip';
import { Links } from '../shared/links';

/** A character: who they are, their family, the stories they appear in, and links to read more. */
@Component({
  selector: 'app-character',
  imports: [RouterLink, I18nPipe, I18nTextPipe, CharacterChip, Links, FamilyTree],
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
  protected readonly stories = computed(() => this.catalog.storiesByCharacter().get(this.id()) ?? []);
}
