import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService, Character } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { CharacterChip } from '../shared/character-chip';
import { Links } from '../shared/links';
import { Sources } from '../shared/sources';

/** A group: who they are, its members, the stories it takes part in, the ancient lists of its members, links. */
@Component({
  selector: 'app-group',
  imports: [RouterLink, I18nPipe, I18nTextPipe, CharacterChip, Links, Sources],
  templateUrl: './group.html',
})
export class GroupPage {
  private readonly catalog = inject(CatalogService);
  /** The :id route parameter. */
  readonly id = input.required<string>();

  protected readonly loaded = computed(() => this.catalog.catalog() !== null);
  protected readonly group = computed(() => this.catalog.groupById().get(this.id()));
  protected readonly members = computed(() =>
    (this.group()?.members ?? []).map((id) => this.catalog.characterById().get(id)).filter((c): c is Character => !!c),
  );
  protected readonly stories = computed(() => this.catalog.storiesByGroup().get(this.id()) ?? []);
}
