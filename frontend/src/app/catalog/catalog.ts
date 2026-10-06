import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { I18nLanguage, I18nText } from '../i18n/i18n.service';

/** The backend's catalog (core: Model.kt), served whole by /api/catalog. */
export type CharacterKind = 'primordial' | 'titan' | 'olympian' | 'deity' | 'nymph' | 'hero' | 'mortal' | 'creature';
export const CHARACTER_KINDS: CharacterKind[] = ['primordial', 'titan', 'olympian', 'deity', 'nymph', 'hero', 'mortal', 'creature'];

export type LinkKind = 'wikipedia' | 'article' | 'video';
export const LINK_KINDS: LinkKind[] = ['wikipedia', 'article', 'video'];

export interface Link {
  kind: LinkKind;
  lang: I18nLanguage;
  title: string;
  url: string;
}

export interface Character {
  id: string;
  name: I18nText;
  kind: CharacterKind;
  /** The Wikidata item: every imported character has one, most curated ones too. */
  wikidata?: string;
  description: I18nText;
  greek?: string;
  roman?: I18nText;
  parents?: string[];
  links?: Link[];
}

export interface Source {
  author: I18nText;
  work: I18nText;
  passage?: string;
  /** A translation online, by i18n language: some, not necessarily all. */
  url?: Partial<Record<I18nLanguage, string>>;
}

export interface Story {
  id: string;
  title: I18nText;
  summary: I18nText;
  characters: string[];
  sources: Source[];
  links?: Link[];
  related?: string[];
}

export interface Cycle {
  id: string;
  title: I18nText;
  description: I18nText;
  stories: string[];
}

export interface Catalog {
  characters: Character[];
  stories: Story[];
  cycles: Cycle[];
}

/** What the server says of itself (backend: Info). */
export interface Info {
  name: string;
  version: string;
  platform: string;
  languages: string[];
}

/** A story's place in one of its cycles, with its neighbours. */
export interface CyclePosition {
  cycle: Cycle;
  position: number;
  previous?: Story;
  next?: Story;
}

/**
 * The catalog, loaded once, and the indexes the navigation follows: a character's stories and children, a story's
 * cycles and neighbours — everything is derived here, the data files hold each relation once.
 */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);
  readonly catalog = signal<Catalog | null>(null);
  readonly info = signal<Info | null>(null);
  readonly unreachable = signal(false);

  readonly characterById = computed(() => new Map((this.catalog()?.characters ?? []).map((c) => [c.id, c])));
  readonly storyById = computed(() => new Map((this.catalog()?.stories ?? []).map((s) => [s.id, s])));

  /** The stories each character appears in, in the cycles' order. */
  readonly storiesByCharacter = computed(() => {
    const index = new Map<string, Story[]>();
    for (const story of this.storiesInCycleOrder()) {
      for (const id of story.characters) index.set(id, [...(index.get(id) ?? []), story]);
    }
    return index;
  });

  /** Each character's children: the characters naming them as a parent. */
  readonly childrenByCharacter = computed(() => {
    const index = new Map<string, Character[]>();
    for (const child of this.catalog()?.characters ?? []) {
      for (const parent of child.parents ?? []) index.set(parent, [...(index.get(parent) ?? []), child]);
    }
    return index;
  });

  /** Every story once, cycle after cycle, in each cycle's order. */
  readonly storiesInCycleOrder = computed(() => {
    const seen = new Set<string>();
    const ordered: Story[] = [];
    for (const cycle of this.catalog()?.cycles ?? []) {
      for (const id of cycle.stories) {
        const story = this.storyById().get(id);
        if (story && !seen.has(id)) {
          seen.add(id);
          ordered.push(story);
        }
      }
    }
    return ordered;
  });

  constructor() {
    this.http.get<Catalog>('api/catalog').subscribe({
      next: (catalog) => this.catalog.set(catalog),
      error: () => this.unreachable.set(true),
    });
    this.http.get<Info>('api/info').subscribe({ next: (info) => this.info.set(info), error: () => this.unreachable.set(true) });
  }

  /** The story's place in each cycle it belongs to. */
  cyclePositions(story: Story): CyclePosition[] {
    return (this.catalog()?.cycles ?? [])
      .filter((cycle) => cycle.stories.includes(story.id))
      .map((cycle) => {
        const index = cycle.stories.indexOf(story.id);
        return {
          cycle,
          position: index + 1,
          previous: this.storyById().get(cycle.stories[index - 1]),
          next: this.storyById().get(cycle.stories[index + 1]),
        };
      });
  }
}
