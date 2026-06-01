import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { skipGlobalError } from '../errors/global-error.interceptor';
import {
  CreatePaymentResponse,
  PaymentStatusResponse,
  TokenPack,
} from './payment.models';

/**
 * Token-pack + payment API client (tickets FE-PAY1, FE-PAY2, FE-PAY3).
 *
 * Crediting is entirely server-driven by the verified monobank webhook (BE-P4); this client only
 * lists packs, kicks off a server-created checkout, and polls payment status. It never computes a
 * signature, touches the merchant token, or credits tokens locally.
 */
@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  // The buy-tokens + status flows render their own inline errors (FE-PAY), so they opt out of the
  // global error toast to avoid double messaging.

  /** GET /tokens/packs — active packs only (FE-PAY1). */
  packs(): Observable<TokenPack[]> {
    return this.http.get<TokenPack[]>(`${this.base}/tokens/packs`, { context: skipGlobalError() });
  }

  /** POST /payments — create/reuse a pending payment and get the checkout params (FE-PAY2). */
  createPayment(packId: string): Observable<CreatePaymentResponse> {
    return this.http.post<CreatePaymentResponse>(
      `${this.base}/payments`,
      { packId },
      { context: skipGlobalError() },
    );
  }

  /** GET /payments/{id} — poll status (FE-PAY3). */
  status(paymentId: string): Observable<PaymentStatusResponse> {
    return this.http.get<PaymentStatusResponse>(
      `${this.base}/payments/${encodeURIComponent(paymentId)}`,
      { context: skipGlobalError() },
    );
  }
}
