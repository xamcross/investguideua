import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { NewsWidgetComponent } from './news-widget.component';
import { NewsService } from './news.service';
import { NewsItem, NewsLatest } from './news.models';

function item(n: number): NewsItem {
  return {
    sourceName: `Src${n}`,
    title: `Headline ${n}`,
    link: `https://example.com/${n}`,
    publishedAt: '2026-07-26T09:00:00Z',
  };
}

describe('NewsWidgetComponent', () => {
  let latestSpy: jasmine.Spy;

  function create(payload?: NewsLatest, fail = false): ComponentFixture<NewsWidgetComponent> {
    latestSpy = fail
      ? jasmine.createSpy('latest').and.returnValue(throwError(() => new Error('down')))
      : jasmine.createSpy('latest').and.returnValue(of(payload));
    TestBed.configureTestingModule({
      imports: [NewsWidgetComponent, TranslateModule.forRoot()],
      providers: [{ provide: NewsService, useValue: { latest: latestSpy } }],
    });
    const fixture = TestBed.createComponent(NewsWidgetComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('renders both lists with outbound links (target=_blank rel=noopener)', () => {
    const fixture = create({ ukraine: [item(1)], world: [item(2)] });

    const links = fixture.nativeElement.querySelectorAll('a.ig-news__link');
    expect(links.length).toBe(2);
    expect(links[0].getAttribute('href')).toBe('https://example.com/1');
    expect(links[0].getAttribute('target')).toBe('_blank');
    expect(links[0].getAttribute('rel')).toBe('noopener');
    expect(fixture.nativeElement.textContent).toContain('Headline 1');
    expect(fixture.nativeElement.textContent).toContain('Src1');
  });

  it('shows at most 5 items per list', () => {
    const many = [1, 2, 3, 4, 5, 6, 7, 8].map(item);
    const fixture = create({ ukraine: many, world: [] });

    expect(fixture.nativeElement.querySelectorAll('a.ig-news__link').length).toBe(5);
  });

  it('renders nothing when the request fails', () => {
    const fixture = create(undefined, true);

    expect(fixture.nativeElement.querySelector('section')).toBeNull();
  });

  it('renders nothing when both lists are empty', () => {
    const fixture = create({ ukraine: [], world: [] });

    expect(fixture.nativeElement.querySelector('section')).toBeNull();
  });
});
