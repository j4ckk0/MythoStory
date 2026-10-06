import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows the stories and the version the backend serves', async () => {
    const fixture = TestBed.createComponent(App);
    http.expectOne('api/info').flush({ name: 'MythoStory', version: '0.1', platform: 'desktop' });
    http.expectOne('api/stories').flush([{ id: '1', title: 'The Odyssey', summary: 'A long way home' }]);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.version')?.textContent).toContain('0.1 · desktop');
    expect(page.querySelector('.stories h2')?.textContent).toContain('The Odyssey');
  });

  it('says so when the backend does not answer', async () => {
    const fixture = TestBed.createComponent(App);
    http.expectOne('api/info').error(new ProgressEvent('error'));
    http.expectOne('api/stories').error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('.error')?.textContent).toContain('does not answer');
  });
});
