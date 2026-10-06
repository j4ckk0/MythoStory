import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CatalogService } from './catalog/catalog';
import { SessionService } from './catalog/session';
import { I18nPipe } from './i18n/i18n.pipe';
import { I18nService } from './i18n/i18n.service';

/** The frame of every page: the header — navigation, search, i18n switch — and the footer. */
@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, I18nPipe],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly catalog = inject(CatalogService);
  protected readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  protected query = '';

  constructor() {
    inject(SessionService).open();
  }

  protected search(): void {
    this.router.navigate(['/search'], { queryParams: { q: this.query.trim() || null } });
  }
}
