import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { OvdpCalculatorComponent } from './ovdp-calculator.component';
import { BondSnapshotService } from './bond-snapshot.service';
import { BondSnapshot } from './bond-snapshot.data';

/**
 * 015: the calculator prerenders a meaningful default result (25 000 грн, 12 міс, 16%):
 * gross = 25 000 x 16% = 4 000 грн - so crawlers see real content, and the custom-rate mode keeps
 * the page useful even when the snapshot is empty.
 */
describe('OvdpCalculatorComponent', () => {
  const FAKE: BondSnapshot = {
    fetchedAt: '2026-07-25T12:02:08Z',
    bonds: [
      { isin: 'UA4000200000', military: true, currency: 'UAH', maturity: '2026-11-18', sellPriceMinor: 100500, buyPriceMinor: 99500, sellYield: 15.25, buyYield: 15.8 },
      { isin: 'UA4000200018', military: false, currency: 'UAH', maturity: '2027-03-01', sellPriceMinor: 98000, buyPriceMinor: 97000, sellYield: 16.1, buyYield: 16.9 },
    ],
  };

  function setup(snapshot: BondSnapshot) {
    TestBed.configureTestingModule({
      imports: [OvdpCalculatorComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BondSnapshotService, useValue: { snapshot: () => snapshot, latest: () => of(snapshot) } },
      ],
    });
    const fixture = TestBed.createComponent(OvdpCalculatorComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  /** uk-UA Intl formatting uses non-breaking thousands separators; normalize before asserting. */
  function plain(text: string | null): string {
    return (text ?? '').replace(/[  ]/g, ' ');
  }

  it('renders the default result (25 000 грн at 16% over 12 months = 4 000 грн gross)', () => {
    const el = setup(FAKE).nativeElement as HTMLElement;
    const result = el.querySelector('.ig-calc__result')!;
    expect(result).toBeTruthy();
    expect(plain(result.textContent)).toContain('4 000');
    expect(el.querySelector('[data-ovdp-asof]')).toBeTruthy();
  });

  it('lists snapshot issues in the select plus the custom-rate option', () => {
    const el = setup(FAKE).nativeElement as HTMLElement;
    expect(el.querySelectorAll('select option').length).toBe(3); // custom + 2 issues
  });

  it('stays functional with an empty snapshot (custom rate only, honest marker)', () => {
    const el = setup({ fetchedAt: null, bonds: [] }).nativeElement as HTMLElement;
    expect(el.querySelector('[data-ovdp-empty]')).toBeTruthy();
    expect(plain(el.querySelector('.ig-calc__result')!.textContent)).toContain('4 000');
  });
});
