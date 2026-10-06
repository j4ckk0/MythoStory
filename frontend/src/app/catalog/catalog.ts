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
  /** Its era's id, on the timeline. */
  era: string;
  /** How many generations before the Trojan War it happens (0: the war's own); absent when the tradition does not say. */
  generation?: number;
  /** The date ancient chronographers gave it. */
  dating?: I18nText;
  /** Its places' ids, on the map; in the journey's order when route is true. */
  places?: string[];
  route?: boolean;
}

/** A period of the mythic chronology (backend: Era). */
export interface Era {
  id: string;
  title: I18nText;
  description: I18nText;
  dating?: I18nText;
}

/** A place on the map (backend: Place); identification is set for a mythical or disputed one. */
export interface Place {
  id: string;
  name: I18nText;
  lat: number;
  lon: number;
  wikidata: string;
  identification?: I18nText;
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
  eras: Era[];
  places: Place[];
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
  readonly eraById = computed(() => new Map((this.catalog()?.eras ?? []).map((e) => [e.id, e])));
  readonly placeById = computed(() => new Map((this.catalog()?.places ?? []).map((p) => [p.id, p])));

  /** The stories happening at each place, in the cycles' order. */
  readonly storiesByPlace = computed(() => {
    const index = new Map<string, Story[]>();
    for (const story of this.storiesInCycleOrder()) {
      for (const id of new Set(story.places ?? [])) index.set(id, [...(index.get(id) ?? []), story]);
    }
    return index;
  });

  /** A story's cycle, the first one it belongs to: its colour on the timeline and the map. */
  readonly cycleOfStory = computed(() => {
    const index = new Map<string, Cycle>();
    for (const cycle of this.catalog()?.cycles ?? []) for (const id of cycle.stories) if (!index.has(id)) index.set(id, cycle);
    return index;
  });

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
