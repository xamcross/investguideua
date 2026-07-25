import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { FooterComponent } from './footer.component';

/**
 * 014-seo-content: the footer renders on every page unconditionally, so it must carry a link to the
 * articles hub — this is the crawl path that keeps the content section connected to the site's
 * link graph in prerendered HTML.
 */
describe('FooterComponent (014: articles link)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [FooterComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('links to the articles hub', () => {
    const fixture = TestBed.createComponent(FooterComponent);
    fixture.detectChanges();
    const hrefs = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a')).map(
      (a) => a.getAttribute('href') ?? '',
    );
    expect(hrefs).toContain('/articles');
  });
});
