import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { LandingComponent } from './landing.component';

/**
 * 014-seo-content: the landing page must link into the articles section — a "latest articles"
 * block with the three newest guides plus a link to the hub — so the prerendered homepage passes
 * link equity to the content section (it previously contained zero article links).
 */
describe('LandingComponent (014: latest-articles section)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LandingComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('renders three latest article links and the all-articles hub link', () => {
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const articleLinks = Array.from(el.querySelectorAll('a'))
      .map((a) => a.getAttribute('href') ?? '')
      .filter((h) => h.startsWith('/articles/'));
    expect(articleLinks.length).toBe(3);

    const hubLink = Array.from(el.querySelectorAll('a')).find(
      (a) => a.getAttribute('href') === '/articles',
    );
    expect(hubLink).withContext('all-articles hub link').toBeTruthy();
  });
});
