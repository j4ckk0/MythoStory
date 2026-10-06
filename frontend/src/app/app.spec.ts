import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { Catalog, CatalogService } from './catalog/catalog';
import { I18nService } from './i18n/i18n.service';
import { en } from './i18n/en';
import { fr } from './i18n/fr';

/** A catalog of two characters and two stories in one cycle: enough to follow every kind of link. */
const CATALOG: Catalog = {
  characters: [
    { id: 'zeus', name: { en: 'Zeus', fr: 'Zeus' }, kind: 'olympian', description: { en: 'King of the gods', fr: 'Roi des dieux' } },
    {
      id: 'heracles', name: { en: 'Heracles', fr: 'Héraclès' }, kind: 'hero', parents: ['zeus'],
      description: { en: 'The strongest hero', fr: 'Le plus fort des héros' },
      links: [
        { kind: 'wikipedia', lang: 'en', title: 'Wikipedia', url: 'https://en.wikipedia.org/wiki/Heracles' },
        { kind: 'wikipedia', lang: 'fr', title: 'Wikipédia', url: 'https://fr.wikipedia.org/wiki/H%C3%A9racl%C3%A8s' },
      ],
    },
  ],
  stories: [
    {
      id: 'labours', title: { en: 'The labours', fr: 'Les travaux' }, characters: ['heracles', 'zeus'],
      summary: { en: 'First paragraph.\n\nSecond paragraph.', fr: 'Premier paragraphe.\n\nSecond paragraphe.' },
      sources: [{ author: { en: 'Apollodorus', fr: 'Apollodore' }, work: { en: 'Library', fr: 'Bibliothèque' }, passage: '2.5', url: { en: 'https://example.org/en' } }],
    },
    {
      id: 'apotheosis', title: { en: 'The apotheosis', fr: 'L’apothéose' }, characters: ['heracles'],
      summary: { en: 'He becomes a god.', fr: 'Il devient dieu.' }, sources: [{ author: { en: 'A', fr: 'A' }, work: { en: 'W', fr: 'W' } }],
    },
  ],
  cycles: [{ id: 'heroes', title: { en: 'Heroes', fr: 'Héros' }, description: { en: 'd', fr: 'd' }, stories: ['labours', 'apotheosis'] }],
};

describe('MythoStory', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter(routes, withComponentInputBinding()), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(url: string) {
    const harness = await RouterTestingHarness.create();
    TestBed.inject(CatalogService); // created by the first page that needs it: here, before its requests are answered
    http.expectOne('api/catalog').flush(CATALOG);
    http.expectOne('api/info').flush({ name: 'MythoStory', version: '0.1', platform: 'desktop', languages: ['en', 'fr'] });
    await harness.navigateByUrl(url);
    return harness.routeNativeElement as HTMLElement;
  }

  it('has every i18n message in French that it has in English', () => {
    expect(Object.keys(fr).sort()).toEqual(Object.keys(en).sort());
  });

  it('shows a story in the reader’s language, with its characters, its cycle neighbours and its sources', async () => {
    TestBed.inject(I18nService).setLanguage('fr');
    const page = await open('/stories/labours');
    expect(page.querySelector('h1')?.textContent).toContain('Les travaux');
    expect(page.querySelectorAll('.summary p').length).toBe(2);
    expect(page.querySelector('.chips')?.textContent).toContain('Héraclès');
    expect(page.querySelector('a[rel=next]')?.textContent).toContain('L’apothéose');
    // the only translation online is English: offered, and marked so
    expect(page.querySelector('.sources .lang')?.textContent).toContain('en');
  });

  it('shows a character’s parents, children and stories', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const zeus = await open('/characters/zeus');
    expect(zeus.querySelector('.facts')?.textContent).toContain('Heracles');
    expect(zeus.querySelector('.entries')?.textContent).toContain('The labours');
  });

  it('lists the characters of a kind', async () => {
    TestBed.inject(I18nService).setLanguage('fr');
    const page = await open('/characters?kind=hero');
    expect(page.querySelector('.entries')?.textContent).toContain('Héraclès');
    expect(page.querySelector('.entries')?.textContent).not.toContain('Zeus');
    expect(page.querySelector('.letters a.active')?.textContent).toContain('Toutes'); // one character: no need to page by letter
  });

  it('finds a character whatever the accents and the language', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const page = await open('/search?q=heracles');
    expect(page.textContent).toContain('Heracles');
    expect(page.textContent).toContain('Heroes and demigods');
  });
});
