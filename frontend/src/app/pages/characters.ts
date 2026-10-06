import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CHARACTER_KINDS, CatalogService, Character, CharacterKind } from '../catalog/catalog';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';
import { I18nService } from '../i18n/i18n.service';
import { normalize } from '../shared/text';

/** Beyond this many characters, the list shows one letter at a time: thousands of links make a slow page. */
const ONE_LETTER_ABOVE = 150;

/**
 * Every character, by kind, in alphabetical order of their name in the reader's language. ?kind= keeps one kind;
 * ?letter= one initial — the first one, when the list would be too long otherwise; ?letter=all lists them all.
 */
@Component({
  selector: 'app-characters',
  imports: [RouterLink, I18nPipe, I18nTextPipe],
  templateUrl: './characters.html',
})
export class Characters {
  private readonly catalog = inject(CatalogService);
  private readonly i18n = inject(I18nService);
  /** The ?kind= query parameter. */
  readonly kind = input<CharacterKind | undefined>();
  /** The ?letter= query parameter: "a"…"z", or "all". */
  readonly letter = input<string | undefined>();

  protected readonly kinds = CHARACTER_KINDS;

  /** The characters of the kind, each with its sorting key: their name in the reader's language, without accents. */
  private readonly ofKind = computed(() => {
    this.i18n.language();
    return (this.catalog.catalog()?.characters ?? [])
      .filter((c) => !this.kind() || c.kind === this.kind())
      .map((character) => ({ character, key: normalize(this.i18n.text(character.name)) }))
      .sort((a, b) => a.key.localeCompare(b.key));
  });

  /** The initials the names of the kind start with. */
  protected readonly letters = computed(() => [...new Set(this.ofKind().map(({ key }) => initial(key)))].sort());

  /** The letter shown: the one asked for, else the first one when the list is long, else all of them. */
  protected readonly currentLetter = computed(() => {
    const asked = this.letter()?.toLowerCase();
    if (asked && (asked === 'all' || this.letters().includes(asked))) return asked;
    return this.ofKind().length > ONE_LETTER_ABOVE ? (this.letters()[0] ?? 'all') : 'all';
  });

  protected readonly count = computed(() => this.ofKind().length);

  protected readonly groups = computed(() => {
    const letter = this.currentLetter();
    const shown = this.ofKind().filter(({ key }) => letter === 'all' || initial(key) === letter);
    return CHARACTER_KINDS.filter((kind) => !this.kind() || kind === this.kind())
      .map((kind) => ({ kind, characters: shown.filter(({ character }) => character.kind === kind).map(({ character }) => character as Character) }))
      .filter((group) => group.characters.length > 0);
  });
}

/** A name's initial for the letter index: "a"…"z", "#" for anything else. */
function initial(key: string): string {
  const first = key.charAt(0);
  return first >= 'a' && first <= 'z' ? first : '#';
}
