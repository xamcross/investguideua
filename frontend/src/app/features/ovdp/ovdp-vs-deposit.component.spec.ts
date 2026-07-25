import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { OvdpVsDepositComponent } from './ovdp-vs-deposit.component';
import { BondSnapshotService } from './bond-snapshot.service';
import { BondSnapshot } from './bond-snapshot.data';

/**
 * 015: the comparison page renders both panels (ОВДП net vs deposit net after 23% taxes), the
 * delta line, and the funnel CTA into /search.
 */
describe('OvdpVsDepositComponent', () => {
  const FAKE: BondSnapshot = {
    fetchedAt: '2026-07-25T12:02:08Z',
    bonds: [
      { isin: 'UA4000200000', military: true, currency: 'UAH', maturity: '2026-11-18', sellPriceMinor: 100500, buyPriceMinor: 99500, sellYield: 16, buyYield: 16.5 },
    ],
  };

  function setup(snapshot: BondSnapshot) {
    TestBed.configureTestingModule({
      imports: [OvdpVsDepositComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BondSnapshotService, useValue: { snapshot: () => snapshot, latest: () => of(snapshot) } },
      ],
    });
    const fixture = TestBed.createComponent(OvdpVsDepositComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('renders both panels, a positive delta for equal rates, and the /search CTA', () => {
    const el = setup(FAKE).nativeElement as HTMLElement;
    expect(el.querySelectorAll('.ig-vs__panel').length).toBe(2);
    // Equal 16% rates: OVDP nets 8 000 грн, deposit only 6 160 грн - delta must render.
    // uk-UA Intl formatting uses non-breaking thousands separators; normalize before asserting.
    const delta = (el.querySelector('.ig-vs__delta')!.textContent ?? '').replace(/[  ]/g, ' ');
    expect(delta).toContain('1 840');
    const hrefs = Array.from(el.querySelectorAll('a')).map((a) => a.getAttribute('href') ?? '');
    expect(hrefs).toContain('/search');
    expect(el.querySelector('[data-ovdp-asof]')).toBeTruthy();
  });
});
