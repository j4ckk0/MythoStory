import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { App } from './app';
import { routes } from './app.routes';
import { Catalog, CatalogService } from './catalog/catalog';
import { I18nService } from './i18n/i18n.service';
import { en } from './i18n/en';
import { fr } from './i18n/fr';

/** A catalog of two characters in one group and two stories in one cycle: enough to follow every kind of link. */
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
      id: 'labours', title: { en: 'The labours', fr: 'Les travaux' }, characters: ['heracles', 'zeus'], groups: ['argonauts'],
      summary: { en: 'First paragraph.\n\nSecond paragraph.', fr: 'Premier paragraphe.\n\nSecond paragraphe.' },
      era: 'heroes', generation: 1, places: ['tiryns', 'nemea'], route: true,
      sources: [{ author: { en: 'Apollodorus', fr: 'Apollodore' }, work: { en: 'Library', fr: 'Bibliothèque' }, passage: '2.5', url: { en: 'https://example.org/en' } }],
    },
    {
      id: 'apotheosis', title: { en: 'The apotheosis', fr: 'L’apothéose' }, characters: ['heracles'], groups: ['argonauts'],
      summary: { en: 'He becomes a god.', fr: 'Il devient dieu.' }, sources: [{ author: { en: 'A', fr: 'A' }, work: { en: 'W', fr: 'W' } }],
      era: 'heroes',
    },
  ],
  groups: [
    {
      id: 'argonauts', name: { en: 'Argonauts', fr: 'Argonautes' }, members: ['heracles', 'zeus'], wikidata: 'Q165510',
      description: { en: 'The crew of the Argo', fr: 'L’équipage de l’Argo' },
      sources: [{ author: { en: 'Apollodorus', fr: 'Apollodore' }, work: { en: 'Library', fr: 'Bibliothèque' }, passage: '1.9.16', url: { fr: 'https://example.org/fr' } }],
    },
  ],
  cycles: [{ id: 'heroes', title: { en: 'Heroes', fr: 'Héros' }, description: { en: 'd', fr: 'd' }, stories: ['labours', 'apotheosis'] }],
  eras: [{ id: 'heroes', title: { en: 'The age of heroes', fr: 'L’âge des héros' }, description: { en: 'd', fr: 'd' } }],
  places: [
    { id: 'tiryns', name: { en: 'Tiryns', fr: 'Tirynthe' }, lat: 37.6, lon: 22.8, wikidata: 'Q217379' },
    { id: 'nemea', name: { en: 'Nemea', fr: 'Némée' }, lat: 37.81, lon: 22.71, wikidata: 'Q748108',
      identification: { en: 'Where the lion lived.', fr: 'Là où vivait le lion.' } },
  ],
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
    // the coastlines, asked for by the pages drawing a map: none needed here
    http.match('map/land.json').forEach((request) => request.flush({ features: [] }));
    harness.detectChanges();
    return harness.routeNativeElement as HTMLElement;
  }

  it('switches the language from the header’s list, which names each language in itself', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const fixture = TestBed.createComponent(App);
    http.expectOne('api/catalog').flush(CATALOG);
    http.expectOne('api/info').flush({ name: 'MythoStory', version: '0.1', platform: 'desktop', languages: ['en', 'fr'] });
    fixture.detectChanges();
    await fixture.whenStable();
    const select = fixture.nativeElement.querySelector('select.i18n-select') as HTMLSelectElement;
    expect([...select.options].map((o) => o.textContent?.trim())).toEqual(['English', 'Français']);
    expect(select.value).toBe('en');
    select.value = 'fr';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(TestBed.inject(I18nService).language()).toBe('fr');
    expect(fixture.nativeElement.querySelector('nav')?.textContent).toContain('Personnages');
  });

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
    expect(zeus.querySelector('.facts')?.textContent).toContain('1 child');
    expect(zeus.querySelector('.families')?.textContent).toContain('Heracles');
    expect(zeus.querySelector('.entries')?.textContent).toContain('The labours');
  });

  it('lists the characters of a kind', async () => {
    TestBed.inject(I18nService).setLanguage('fr');
    const page = await open('/characters?kind=hero');
    expect(page.querySelector('.entries')?.textContent).toContain('Héraclès');
    expect(page.querySelector('.entries')?.textContent).not.toContain('Zeus');
    expect(page.querySelector('.letters a.active')?.textContent).toContain('Toutes'); // one character: no need to page by letter
  });

  it('places the stories on the timeline by generation, the undated ones last', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const page = await open('/timeline');
    const rows = [...page.querySelectorAll('.generation')].map((h) => h.textContent?.trim());
    expect(rows).toEqual(['The generation before the Trojan War', 'At a time the tradition does not say']);
    expect(page.querySelector('.card .card-title')?.textContent).toContain('The labours');
  });

  it('draws the places and tells what happens at the one selected', async () => {
    TestBed.inject(I18nService).setLanguage('fr');
    const page = await open('/map?place=nemea');
    expect(page.querySelectorAll('.place').length).toBe(2);
    expect(page.querySelectorAll('.place.uncertain').length).toBe(1); // Nemea comes with an identification
    expect(page.querySelector('.route')).not.toBeNull();
    expect(page.querySelector('.place-panel')?.textContent).toContain('Là où vivait le lion');
    expect(page.querySelector('.place-panel')?.textContent).toContain('Les travaux');
  });

  it('draws a character’s ancestors and groups the children by the other parent', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const heracles = await open('/characters/heracles');
    expect(heracles.querySelector('.pedigree .node.root')?.textContent).toContain('Heracles');
    expect(heracles.querySelectorAll('.pedigree .node').length).toBe(2);
  });

  it('shows a group’s members, its stories and who lists them', async () => {
    TestBed.inject(I18nService).setLanguage('fr');
    const page = await open('/groups/argonauts');
    expect(page.querySelector('h1')?.textContent).toContain('Argonautes');
    expect(page.querySelectorAll('.members .chip').length).toBe(2);
    expect(page.querySelector('.entries')?.textContent).toContain('Les travaux');
    expect(page.querySelector('.sources')?.textContent).toContain('1.9.16');
  });

  it('lists the groups with their number of members', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const page = await open('/groups');
    expect(page.querySelector('.entries')?.textContent).toContain('Argonauts');
    expect(page.querySelector('.entries')?.textContent).toContain('2 members');
  });

  it('says which groups a character belongs to', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const heracles = await open('/characters/heracles');
    expect(heracles.querySelector('.facts .group-chip')?.textContent).toContain('Argonauts');
  });

  it('lists the stories a character takes part in through their groups, each once', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    // Zeus is named in the labours, and is one of the test's Argonauts, who take part in both stories
    const stories = [...(await open('/characters/zeus')).querySelectorAll('.entries li')].map((li) => li.textContent?.replace(/\s+/g, ' ').trim());
    expect(stories).toEqual(['The labours', 'The apotheosis — with the Argonauts']);
  });

  it('shows the groups a story involves among its characters', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const story = await open('/stories/labours');
    expect(story.querySelector('.chips .group-chip')?.textContent).toContain('Argonauts');
  });

  it('finds a group by its name', async () => {
    TestBed.inject(I18nService).setLanguage('fr');
    const page = await open('/search?q=argonaute');
    expect(page.textContent).toContain('Argonautes');
  });

  it('finds a character whatever the accents and the language', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const page = await open('/search?q=heracles');
    expect(page.textContent).toContain('Heracles');
    expect(page.textContent).toContain('Heroes and demigods');
  });
  it('shows an empty search page once the query is cleared, without a ?q=', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const page = await open('/search');
    expect(page.querySelector('h1')).not.toBeNull();
  });

  it('keeps one history entry for a search, however many letters are typed', async () => {
    TestBed.inject(I18nService).setLanguage('en');
    const fixture = TestBed.createComponent(App);
    http.expectOne('api/catalog').flush(CATALOG);
    http.expectOne('api/info').flush({ name: 'MythoStory', version: '0.1', platform: 'desktop', languages: ['en', 'fr'] });
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate');
    const input = fixture.nativeElement.querySelector('form.search input') as HTMLInputElement;
    for (const typed of ['h', 'he', 'her']) {
      input.value = typed;
      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();
    }
    // the first letter opens the search page, the next ones replace its query
    expect(navigate.mock.calls.map(([, extras]) => !!extras?.replaceUrl)).toEqual([false, true, true]);
    expect(router.url).toBe('/search?q=her');
  });
});
