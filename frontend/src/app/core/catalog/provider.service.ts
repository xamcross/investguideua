import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { skipGlobalError } from '../errors/global-error.interceptor';
import { Provider } from './provider.models';

/**
 * Read-only provider catalog client (ticket FE-ACCT2, §5.1 `/providers`).
 *
 * Returns the active catalog — the bounded universe recommendations are drawn from (§8.3). The
 * transparency page renders its own inline load error, so this opts out of the global error toast.
 */
@Injectable({ providedIn: 'root' })
export class ProviderService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** GET /providers — active providers only (read-only). */
  list(): Observable<Provider[]> {
    return this.http.get<Provider[]>(`${this.base}/providers`, { context: skipGlobalError() });
  }
}
