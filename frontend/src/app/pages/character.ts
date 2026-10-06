import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CHARACTER_KINDS, CatalogService, Character } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { I18nService } from '../i18n/i18n.service';
import { normalize } from '../shared/text';
import { CharacterChip } from '../shared/character-chip';
import { Links } from '../shared/links';

/** A character: who they are, their family, the stories they appear in, and links to read more. */
@Component({
  selector: 'app-character',
  imports: [RouterLink, I18nPipe, I18nTextPipe, CharacterChip, Links],
  templateUrl: './character.html',
})
export class CharacterPage {
  private readonly catalog = inject(CatalogService);
  private readonly i18n = inject(I18nService);
  /** The :id route parameter. */
  readonly id = input.required<string>();

  protected readonly loaded = computed(() => this.catalog.catalog() !== null);
  protected readonly character = computed(() => this.catalog.characterById().get(this.id()));
  protected readonly parents = computed(() =>
    (this.character()?.parents ?? []).map((id) => this.catalog.characterById().get(id)).filter((c): c is Character => !!c),
  );
  /** The children — dozens for Zeus — by kind, then in alphabetical order of their name in the reader's language. */
  protected readonly children = computed(() => {
    const name = (c: Character) => normalize(this.i18n.text(c.name));
    return [...(this.catalog.childrenByCharacter().get(this.id()) ?? [])].sort(
      (a, b) => CHARACTER_KINDS.indexOf(a.kind) - CHARACTER_KINDS.indexOf(b.kind) || name(a).localeCompare(name(b)),
    );
  });
  protected readonly stories = computed(() => this.catalog.storiesByCharacter().get(this.id()) ?? []);
}
