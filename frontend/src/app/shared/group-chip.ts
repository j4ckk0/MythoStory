import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Group } from '../catalog/catalog';
import { I18nTextPipe } from '../i18n/i18n.pipe';

/** A group's name as a link to its page, in the groups' colour; its description on hover. */
@Component({
  selector: 'app-group-chip',
  imports: [RouterLink, I18nTextPipe],
  template: `<a class="chip group-chip" [routerLink]="['/groups', group().id]" data-kind="group"
    [title]="group().description | i18nText">{{ group().name | i18nText }}</a>`,
})
export class GroupChip {
  readonly group = input.required<Group>();
}
