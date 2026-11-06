import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService, CharacterKind, Group } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { I18nService } from '../i18n/i18n.service';
import { normalize } from '../shared/text';

/** The sections of the groups' list, in their order. */
export type GroupSection = 'heroes' | 'gods' | 'monsters';
const GROUP_SECTIONS: GroupSection[] = ['heroes', 'gods', 'monsters'];

/** A character kind's section: a group goes in the section most of its members' kinds point to. */
const SECTION_OF_KIND: Record<CharacterKind, GroupSection> = {
  hero: 'heroes', mortal: 'heroes',
  primordial: 'gods', titan: 'gods', olympian: 'gods', deity: 'gods', nymph: 'gods',
  creature: 'monsters',
};

/** Every group, by section — heroes and mortals, gods and nymphs, monsters —, in alphabetical order of their name. */
@Component({
  selector: 'app-groups',
  imports: [RouterLink, I18nPipe, I18nTextPipe],
  template: `
    <h1>{{ 'groups.title' | i18n }}</h1>
    <p class="lead">{{ 'groups.lead' | i18n }}</p>
    @for (section of sections(); track section.section) {
      <section>
        <h2>{{ 'groups.section.' + section.section | i18n }}</h2>
        <ul class="entries">
          @for (group of section.groups; track group.id) {
            <li>
              <a [routerLink]="['/groups', group.id]">{{ group.name | i18nText }}</a>
              <span class="muted"> — {{ (group.members.length === 1 ? 'group.members.one' : 'group.members') | i18n: { count: group.members.length } }}
                · {{ group.description | i18nText }}</span>
            </li>
          }
        </ul>
      </section>
    }
  `,
})
export class Groups {
  private readonly catalog = inject(CatalogService);
  private readonly i18n = inject(I18nService);

  protected readonly sections = computed(() => {
    this.i18n.language();
    const groups = [...(this.catalog.catalog()?.groups ?? [])].sort((a, b) =>
      normalize(this.i18n.text(a.name)).localeCompare(normalize(this.i18n.text(b.name))),
    );
    return GROUP_SECTIONS.map((section) => ({ section, groups: groups.filter((g) => this.sectionOf(g) === section) })).filter(
      (s) => s.groups.length > 0,
    );
  });

  /** The section most of the group's members belong to; the first of them on a tie. */
  private sectionOf(group: Group): GroupSection {
    const counts = new Map<GroupSection, number>();
    for (const id of group.members) {
      const kind = this.catalog.characterById().get(id)?.kind;
      if (kind) counts.set(SECTION_OF_KIND[kind], (counts.get(SECTION_OF_KIND[kind]) ?? 0) + 1);
    }
    return GROUP_SECTIONS.reduce((best, s) => ((counts.get(s) ?? 0) > (counts.get(best) ?? 0) ? s : best), GROUP_SECTIONS[0]);
  }
}
