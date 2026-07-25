import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { BondSnapshotService } from './bond-snapshot.service';
import { BondSnapshot } from './bond-snapshot.data';

/**
 * Fix after 015: the backend ingest never deletes, so /latest could historically return matured
 * "zombie" quotes. The backend now filters them, and this client-side sanitizer mirrors that as
 * defense-in-depth (an old backend, or an aged committed fallback, must never render matured rows).
 */
describe('BondSnapshotService', () => {
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  const daysFromNow = (n: number) => iso(new Date(Date.now() + n * 86_400_000));

  function row(isin: string, maturity: string) {
    return {
      isin, military: true, currency: 'UAH', maturity, quotationDate: maturity,
      sellPriceMinor: 100500, buyPriceMinor: 99500, sellYield: 15.25, buyYield: 15.8,
      fetchedAt: '2026-07-25T09:00:00Z',
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('latest() drops matured bonds and keeps current ones', () => {
    const service = TestBed.inject(BondSnapshotService);
    let result: BondSnapshot | undefined;
    service.latest().subscribe((s) => (result = s));

    TestBed.inject(HttpTestingController)
      .expectOne(`${environment.apiBaseUrl}/bond-prices/latest`)
      .flush([row('UA4000000012', daysFromNow(90)), row('UA4000000023', daysFromNow(-30))]);

    expect(result!.bonds.map((b) => b.isin)).toEqual(['UA4000000012']);
  });
});
