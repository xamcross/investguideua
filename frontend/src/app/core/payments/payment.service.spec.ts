import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PaymentService } from './payment.service';
import { CreatePaymentResponse, PaymentStatusResponse, TokenPack } from './payment.models';
import { environment } from '../../../environments/environment';

/**
 * QA2 critical flow: buy-tokens -> checkout -> status. Asserts the client kicks off a server-created
 * checkout (the pinned BE-P2 schema, no client-side signing) and polls status to a terminal state
 * (FE-PAY2 / FE-PAY3). Crediting is server-driven; the client only reflects state.
 */
describe('PaymentService (QA2: buy tokens -> checkout -> status)', () => {
  const base = environment.apiBaseUrl;
  let service: PaymentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PaymentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists active packs with integer minor-unit prices', () => {
    let packs: TokenPack[] | undefined;
    service.packs().subscribe((p) => (packs = p));
    const req = httpMock.expectOne(`${base}/tokens/packs`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'pack-10', tokens: 10, priceMinorUnits: 16_900, currency: 'UAH' }] as TokenPack[]);

    expect(packs!.length).toBe(1);
    expect(Number.isInteger(packs![0].priceMinorUnits)).toBeTrue();
  });

  it('createPayment returns the pinned checkout schema and never signs anything client-side', () => {
    let res: CreatePaymentResponse | undefined;
    service.createPayment('pack-10').subscribe((r) => (res = r));

    const req = httpMock.expectOne(`${base}/payments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ packId: 'pack-10' });

    const checkout: CreatePaymentResponse = {
      paymentId: 'pay-1',
      orderId: 'order-1',
      providerInvoiceId: 'p2_9ZgpZVsl3',
      pageUrl: 'https://pay.mbnk.biz/p2_9ZgpZVsl3',
    };
    req.flush(checkout);

    // The client receives a redirect pageUrl (no signature/secret); it polls status via paymentId (FE-PAY3).
    expect(res).toEqual(checkout);
    expect(res!.paymentId).toBe('pay-1');
    expect(res!.pageUrl).toBe('https://pay.mbnk.biz/p2_9ZgpZVsl3');
  });

  it('status polling reflects the server-driven transition to success', () => {
    let status: PaymentStatusResponse | undefined;
    service.status('pay-1').subscribe((s) => (status = s));

    const req = httpMock.expectOne(`${base}/payments/pay-1`);
    expect(req.request.method).toBe('GET');
    req.flush({
      paymentId: 'pay-1',
      orderId: 'order-1',
      packId: 'pack-10',
      status: 'success',
      amountMinorUnits: 16_900,
      currency: 'UAH',
      tokensToCredit: 10,
    } as PaymentStatusResponse);

    expect(status!.status).toBe('success'); // FE-PAY3: balance is then refreshed from the server
    expect(status!.tokensToCredit).toBe(10);
  });
});
