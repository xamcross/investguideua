import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InvestmentService } from './investment.service';
import { SearchResponse } from './investment.models';
import { AuthService } from '../auth/auth.service';
import { AuthResponse, UserProfile } from '../auth/auth.models';
import { environment } from '../../../environments/environment';

/**
 * QA2 critical flow: search happy path. Asserts the request shape and that the server-authoritative
 * balance is mirrored into the shared auth state without a manual refresh (FE-SEARCH2 DoD). Disclaimer
 * RENDERING is asserted in results.component.spec.ts; this covers the data path.
 */
describe('InvestmentService (QA2: search happy path + balance mirror)', () => {
  const base = environment.apiBaseUrl;
  let service: InvestmentService;
  let auth: AuthService;
  let httpMock: HttpTestingController;

  const seededUser: UserProfile = {
    userId: 'u1',
    email: 'u@example.com',
    emailVerified: true,
    tokenBalance: 5,
    roles: ['USER'],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InvestmentService);
    auth = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);

    // Seed a verified, in-session user (balance 5) so the post-search balance mirror has a target.
    auth.login('u@example.com', 'pw').subscribe();
    httpMock
      .expectOne(`${base}/auth/login`)
      .flush({ accessToken: 'a1', expiresInMs: 900000, user: seededUser } as AuthResponse);
  });

  afterEach(() => httpMock.verify());

  it('POSTs the search and mirrors the returned balance (5 -> 4) into the auth state', () => {
    let response: SearchResponse | undefined;
    service
      .search({ amount: 50_000_000, currency: 'UAH', horizon: 'MEDIUM', riskTolerance: 'LOW', goals: null })
      .subscribe((r) => (response = r));

    const req = httpMock.expectOne(`${base}/investments/search`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.currency).toBe('UAH');

    const serverResponse: SearchResponse = {
      requestId: 'req-1',
      tokenBalance: 4, // server debited one
      amount: 50_000_000,
      currency: 'UAH',
      options: [
        {
          providerId: 'privatbank',
          providerName: 'PrivatBank',
          instrument: 'Депозит',
          category: 'BANK_DEPOSIT',
          currency: 'UAH',
          expectedReturnPct: { min: 13, max: 15 },
          riskLevel: 'LOW',
          minAmount: 100_000,
          liquidity: 'строковий',
          rationale: 'надійний банк',
          sourceUrl: 'https://privatbank.ua',
        },
      ],
      disclaimer: 'Це інформаційний матеріал, а не індивідуальна інвестиційна рекомендація.',
    };
    req.flush(serverResponse);

    expect(response).toEqual(serverResponse);
    expect(response!.disclaimer).toBeTruthy(); // AC #5: response always carries the disclaimer
    // Balance mirrored from the server's authoritative value, no manual refresh.
    expect(auth.tokenBalance()).toBe(4);
  });
});
