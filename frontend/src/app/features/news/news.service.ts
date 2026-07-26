import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { skipGlobalError } from '../../core/errors/global-error.interceptor';
import { NewsLatest } from './news.models';

/**
 * Read access for the news widget (feature 016). skipGlobalError: a failed news fetch must never
 * toast - the widget silently hides instead (same pattern as BondSnapshotService.latest()).
 */
@Injectable({ providedIn: 'root' })
export class NewsService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  latest(): Observable<NewsLatest> {
    return this.http.get<NewsLatest>(`${this.base}/news/latest`, { context: skipGlobalError() });
  }
}
