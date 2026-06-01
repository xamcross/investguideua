import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { ResultsComponent } from './results.component';
import { SearchResponse } from '../../core/investment/investment.models';

/**
 * QA2 critical flow: disclaimer rendering on the results view (and, by reuse, the history detail
 * view — both render through this same component, FE-SEARCH2 / FE-HIST2). Asserts AC #5: the standard
 * disclaimer is always shown, and the currency-risk disclaimer appears only when present.
 */
describe('ResultsComponent (QA2: disclaimer rendering / AC #5)', () => {
  const STANDARD = 'Це інформаційний матеріал, а не індивідуальна інвестиційна рекомендація.';
  const CURRENCY_RISK = 'Деякі варіанти номіновані в іншій валюті, ніж сума вашого запиту.';

  let fixture: ComponentFixture<ResultsComponent>;

  const baseResult = (overrides: Partial<SearchResponse> = {}): SearchResponse => ({
    requestId: 'req-1',
    tokenBalance: 4,
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
    disclaimer: STANDARD,
    ...overrides,
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResultsComponent],
      providers: [provideTranslateService({ fallbackLang: 'en', lang: 'en' })],
    }).compileComponents();
    // Seed just the keys this component renders so the `translate` pipe resolves to real copy
    // (no HTTP loader in unit tests).
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      results: {
        noOptions: 'No matching options for these inputs. Try a different amount, currency, or preferences.',
        expectedReturn: 'Expected return',
        currency: 'Currency',
        minAmount: 'Min amount',
        liquidity: 'Liquidity',
      },
      common: { officialSource: 'Official source', perYear: '/ yr' },
    });
    translate.use('en');
    fixture = TestBed.createComponent(ResultsComponent);
  });

  function textOf(): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('always renders the standard financial disclaimer on a successful result', () => {
    fixture.componentInstance.result = baseResult();
    expect(textOf()).toContain(STANDARD);
  });

  it('renders the currency-risk disclaimer only when the server includes it', () => {
    fixture.componentInstance.result = baseResult({ currencyRiskDisclaimer: CURRENCY_RISK });
    const text = textOf();
    expect(text).toContain(STANDARD);
    expect(text).toContain(CURRENCY_RISK);
  });

  it('omits the currency-risk disclaimer when the server does not include it', () => {
    fixture.componentInstance.result = baseResult();
    expect(textOf()).not.toContain(CURRENCY_RISK);
  });

  it('still shows the disclaimer on an empty (no-match) result alongside the no-options message', () => {
    fixture.componentInstance.result = baseResult({ options: [] });
    const text = textOf();
    expect(text).toContain('No matching options');
    expect(text).toContain(STANDARD); // AC #5 holds even when there are no options
  });
});
