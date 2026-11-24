import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { CatalogService } from '../catalog/catalog';
import { I18nText } from '../i18n/i18n.service';
import { I18nPipe, I18nTextPipe } from '../i18n/i18n.pipe';

/** A piece of software or data the application carries, and its licence (backend: ThirdPartyComponent). */
export interface ThirdPartyComponent {
  name: string;
  version: string;
  licence: string;
  url?: string;
  role?: I18nText;
  artifact?: string;
}

/** What /api/licences lists: the embedded components (the JRE, Piper, its voices) and the server's libraries. */
export interface Licences {
  embedded: ThirdPartyComponent[];
  libraries: ThirdPartyComponent[];
}

/** SPDX identifiers whose text spdx.org has: each licence links to it. */
const SPDX = /^[A-Za-z0-9.+-]+$/;

/** How to use the application, and what it carries under which licences. */
@Component({
  selector: 'app-help',
  imports: [NgTemplateOutlet, RouterLink, I18nPipe, I18nTextPipe],
  templateUrl: './help.html',
  styleUrl: './help.css',
})
export class Help {
  protected readonly catalog = inject(CatalogService);
  protected readonly licences = toSignal(
    inject(HttpClient)
      .get<Licences>('api/licences')
      .pipe(catchError(() => of<Licences>({ embedded: [], libraries: [] }))),
  );

  /** A licence expression's identifiers, each with its text's address on spdx.org when it is one. */
  protected parts(licence: string): { id: string; url?: string; or: boolean }[] {
    return licence.split(/ (OR|WITH) /).flatMap((part, i, all) => {
      if (part === 'OR' || part === 'WITH') return [];
      const joiner = all[i - 1];
      const id = joiner === 'WITH' ? `with ${part}` : part;
      const url = SPDX.test(part) ? `https://spdx.org/licenses/${part}.html` : undefined;
      return [{ id, url, or: joiner === 'OR' }];
    });
  }
}
