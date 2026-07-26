import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { LandingComponent } from './landing.component';
import { ArticleService } from '../articles/article.service';
import { NewsService } from '../news/news.service';

/**
 * 014-seo-content: the landing page must link into the articles section — a "latest articles"
 * block with the three newest guides plus a link to the hub — so the prerendered homepage passes
 * link equity to the content section (it previously contained zero article links).
 *
 * ArticleService is stubbed: the real service imports the build-generated (gitignored)
 * articles.data.ts, which must not become a test dependency on a clean checkout, and stubbing four
 * items lets the test genuinely prove the slice(0, 3) cap rather than couple to editorial volume.
 */
describe('LandingComponent (014: latest-articles section)', () => {
  const FAKE_ARTICLES = [
    { slug: 'first-guide', title: 'Перший гайд', summary: 'Опис першого гайда.' },
    { slug: 'second-guide', title: 'Другий гайд', summary: 'Опис другого гайда.' },
    { slug: 'third-guide', title: 'Третій гайд', summary: 'Опис третього гайда.' },
    { slug: 'fourth-guide', title: 'Четвертий гайд', summary: 'Опис четвертого гайда.' },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LandingComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ArticleService, useValue: { list: () => FAKE_ARTICLES } },
        { provide: NewsService, useValue: { latest: () => of({ ukraine: [], world: [] }) } },
      ],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('renders the three newest article links (slice cap) and the all-articles hub link', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const articleLinks = Array.from(el.querySelectorAll('a'))
      .map((a) => a.getAttribute('href') ?? '')
      .filter((h) => h.startsWith('/articles/'));
    expect(articleLinks).toEqual([
      '/articles/first-guide',
      '/articles/second-guide',
      '/articles/third-guide',
    ]);

    const hubLink = Array.from(el.querySelectorAll('a')).find(
      (a) => a.getAttribute('href') === '/articles',
    );
    expect(hubLink).withContext('all-articles hub link').toBeTruthy();
  });

  it('hides the whole section when no articles are available', () => {
    TestBed.overrideProvider(ArticleService, { useValue: { list: () => [] } });
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.ig-articles')).toBeNull();
  });

  it('embeds the news widget', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('ig-news-widget')).toBeTruthy();
  });
});
