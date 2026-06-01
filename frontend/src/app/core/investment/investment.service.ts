import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { skipGlobalError } from '../errors/global-error.interceptor';
import { HistoryPage, SearchRequestBody, SearchResponse } from './investment.models';

/**
 * Investment search + history API client (tickets FE-SEARCH, FE-HIST).
 *
 * Crediting/debiting is entirely server-driven; the client only reflects the authoritative
 * `tokenBalance` the server returns. After a successful search we mirror the new balance into the
 * shared auth state so the nav updates without a manual refresh (FE-SEARCH2 DoD).
 */
@Injectable({ providedIn: 'root' })
export class InvestmentService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = environment.apiBaseUrl;

  // These flows render their own contextual inline error UI (FE-SEARCH3 / FE-HIST), so they opt out
  // of the global error toast (skipGlobalError) to avoid double messaging. The backend remains the
  // source of truth and the error still propagates to the component handlers.

  search(body: SearchRequestBody): Observable<SearchResponse> {
    return this.http
      .post<SearchResponse>(`${this.base}/investments/search`, body, { context: skipGlobalError() })
      .pipe(tap((res) => this.auth.updateTokenBalance(res.tokenBalance)));
  }

  history(page: number, size: number): Observable<HistoryPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<HistoryPage>(`${this.base}/investments/history`, {
      params,
      context: skipGlobalError(),
    });
  }

  getById(id: string): Observable<SearchResponse> {
    return this.http.get<SearchResponse>(`${this.base}/investments/${id}`, {
      context: skipGlobalError(),
    });
  }
}
