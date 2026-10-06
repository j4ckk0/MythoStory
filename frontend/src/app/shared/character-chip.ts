import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Character } from '../catalog/catalog';
import { I18nTextPipe } from '../i18n/i18n.pipe';

/** A character's name as a link to their page, coloured by their kind; their description on hover, which tells namesakes apart. */
@Component({
  selector: 'app-character-chip',
  imports: [RouterLink, I18nTextPipe],
  template: `<a class="chip" [routerLink]="['/characters', character().id]" [attr.data-kind]="character().kind"
    [title]="character().description | i18nText">{{
    character().name | i18nText
  }}</a>`,
})
export class CharacterChip {
  readonly character = input.required<Character>();
}
