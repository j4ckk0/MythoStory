import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CatalogService } from './catalog/catalog';
import { SessionService } from './server/session';
import { Player } from './speech/player';
import { I18nPipe } from './i18n/i18n.pipe';
import { I18N_LANGUAGE_NAMES, I18N_LANGUAGES, I18nService } from './i18n/i18n.service';

/** The frame of every page: the header — navigation, search, i18n language list — and the footer. */
@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, I18nPipe, Player],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly catalog = inject(CatalogService);
  protected readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  protected query = '';
  protected readonly languages = I18N_LANGUAGES;
  protected readonly languageNames = I18N_LANGUAGE_NAMES;

  constructor() {
    inject(SessionService).open();
  }

  /** Each letter typed searches again: the first opens the search page, the next ones replace its query in the history. */
  protected search(): void {
    const searching = this.router.url.startsWith('/search');
    this.router.navigate(['/search'], { queryParams: { q: this.query.trim() || null }, replaceUrl: searching });
  }
}
