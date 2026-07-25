import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { OvdpRatesComponent } from './ovdp-rates.component';
import { BondSnapshotService } from './bond-snapshot.service';
import { BondSnapshot } from './bond-snapshot.data';
import { StructuredDataService } from '../../core/seo/structured-data.service';

/**
 * 015-ovdp-data-pages: the rates page renders the bundled snapshot (so prerendered HTML always
 * carries the table + as-of stamp for crawlers) and links into the calculator pages. With an empty
 * snapshot it must show the honest empty state instead of the table.
 */
describe('OvdpRatesComponent', () => {
  const FAKE: BondSnapshot = {
    fetchedAt: '2026-07-25T12:02:08Z',
    bonds: [
      { isin: 'UA4000200000', military: true, currency: 'UAH', maturity: '2026-11-18', sellPriceMinor: 100500, buyPriceMinor: 99500, sellYield: 15.25, buyYield: 15.8 },
      { isin: 'UA4000200018', military: false, currency: 'UAH', maturity: '2027-03-01', sellPriceMinor: 98000, buyPriceMinor: 97000, sellYield: 16.1, buyYield: 16.9 },
    ],
  };

  function setup(snapshot: BondSnapshot) {
    TestBed.configureTestingModule({
      imports: [OvdpRatesComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BondSnapshotService, useValue: { snapshot: () => snapshot, latest: () => of(snapshot) } },
      ],
    });
    const fixture = TestBed.createComponent(OvdpRatesComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('renders one table row per bond plus the as-of stamp and calculator links', () => {
    const el = setup(FAKE).nativeElement as HTMLElement;
    expect(el.querySelectorAll('tbody tr').length).toBe(2);
    expect(el.querySelector('[data-ovdp-asof]')).toBeTruthy();
    expect(el.querySelector('[data-ovdp-empty]')).toBeNull();
    const hrefs = Array.from(el.querySelectorAll('a')).map((a) => a.getAttribute('href') ?? '');
    expect(hrefs).toContain('/ovdp/calculator');
    expect(hrefs).toContain('/ovdp/vs-deposit');
  });

  it('shows the honest empty state when the snapshot has no bonds', () => {
    const el = setup({ fetchedAt: null, bonds: [] }).nativeElement as HTMLElement;
    expect(el.querySelector('[data-ovdp-empty]')).toBeTruthy();
    expect(el.querySelector('table')).toBeNull();
  });

  it('does NOT re-inject page JSON-LD after destroy when the language changes (leak guard)', () => {
    const fixture = setup(FAKE);
    const structured = TestBed.inject(StructuredDataService);
    const spy = spyOn(structured, 'setPageBlocks');

    fixture.destroy();
    TestBed.inject(TranslateService).use('en');

    expect(spy).not.toHaveBeenCalled();
  });
});
