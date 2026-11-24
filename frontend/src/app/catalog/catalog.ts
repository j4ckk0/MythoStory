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
  /** Its groups' ids: the Argonauts in the voyage of the Argo… */
  groups?: string[];
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

/** A group the myths name as one (backend: Group): the Argonauts, the Muses… Its members are characters. */
export interface Group {
  id: string;
  name: I18nText;
  description: I18nText;
  /** Its members' ids, in the order of the list it comes from. */
  members: string[];
  wikidata?: string;
  /** Where the ancient texts list its members. */
  sources?: Source[];
  links?: Link[];
}

export interface Catalog {
  characters: Character[];
  /** Absent when there is none: the server leaves out what is empty by default (core: Catalog). */
  groups?: Group[];
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
  /** The voices the server reads aloud with (/api/speech): the desktop's Piper; each language's default first. */
  voices?: Voice[];
}

/** A voice of the server (backend: Voice): its i18n language, name and what it sounds like. */
export interface Voice {
  id: string;
  language: string;
  name: string;
  description?: I18nText;
}

/** A story's place in one of its cycles, with its neighbours. */
export interface CyclePosition {
  cycle: Cycle;
  position: number;
  previous?: Story;
  next?: Story;
}

/** Adds a value to the list a key has in an index: how the indexes below are built. */
function addTo<K, V>(index: Map<K, V[]>, key: K, value: V): void {
  const list = index.get(key);
  if (list) list.push(value);
  else index.set(key, [value]);
}

/** The records of these ids that an index holds, in the ids' order: an id the catalog lacks is left out. */
function known<T>(ids: readonly string[] | undefined, byId: Map<string, T>): T[] {
  return (ids ?? []).flatMap((id) => {
    const record = byId.get(id);
    return record ? [record] : [];
  });
}

/**
 * The catalog, loaded once, and the indexes the navigation follows: a character's stories, children and groups, a
 * group's stories, a story's cycles and neighbours — everything is derived here, the data files hold each relation once.
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
  readonly groupById = computed(() => new Map((this.catalog()?.groups ?? []).map((g) => [g.id, g])));

  /** The groups each character belongs to, in alphabetical order of their English name. */
  readonly groupsByCharacter = computed(() => {
    const index = new Map<string, Group[]>();
    const groups = [...(this.catalog()?.groups ?? [])].sort((a, b) => (a.name.en ?? '').localeCompare(b.name.en ?? ''));
    for (const group of groups) for (const id of group.members) addTo(index, id, group);
    return index;
  });

  /** The stories each group takes part in, in the cycles' order. */
  readonly storiesByGroup = computed(() => {
    const index = new Map<string, Story[]>();
    for (const story of this.storiesInCycleOrder()) {
      for (const id of story.groups ?? []) addTo(index, id, story);
    }
    return index;
  });

  /** The stories happening at each place, in the cycles' order. */
  readonly storiesByPlace = computed(() => {
    const index = new Map<string, Story[]>();
    for (const story of this.storiesInCycleOrder()) {
      for (const id of new Set(story.places ?? [])) addTo(index, id, story);
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
      for (const id of story.characters) addTo(index, id, story);
    }
    return index;
  });

  /** Each character's children: the characters naming them as a parent. */
  readonly childrenByCharacter = computed(() => {
    const index = new Map<string, Character[]>();
    for (const child of this.catalog()?.characters ?? []) {
      for (const parent of child.parents ?? []) addTo(index, parent, child);
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

  /** The characters of these ids, in their order (the ones the catalog lacks left out). */
  charactersOf(ids: readonly string[] | undefined): Character[] {
    return known(ids, this.characterById());
  }

  /** The stories of these ids, in their order (the ones the catalog lacks left out). */
  storiesOf(ids: readonly string[] | undefined): Story[] {
    return known(ids, this.storyById());
  }

  /** The groups of these ids, in their order (the ones the catalog lacks left out). */
  groupsOf(ids: readonly string[] | undefined): Group[] {
    return known(ids, this.groupById());
  }

  /** The places of these ids, in their order (the ones the catalog lacks left out). */
  placesOf(ids: readonly string[] | undefined): Place[] {
    return known(ids, this.placeById());
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
