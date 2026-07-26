import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { NewsService } from './news.service';
import { NewsLatest } from './news.models';

describe('NewsService', () => {
  let service: NewsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(NewsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs /news/latest and returns both lists', () => {
    const payload: NewsLatest = {
      ukraine: [
        {
          sourceName: 'ЕП',
          title: 'Заголовок',
          link: 'https://ua.example.com/1',
          publishedAt: '2026-07-26T09:00:00Z',
        },
      ],
      world: [],
    };
    let result: NewsLatest | undefined;

    service.latest().subscribe((r) => (result = r));
    const req = http.expectOne(`${environment.apiBaseUrl}/news/latest`);
    expect(req.request.method).toBe('GET');
    req.flush(payload);

    expect(result).toEqual(payload);
  });
});
