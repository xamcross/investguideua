import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { SearchComponent } from './search.component';
import { AuthService } from '../../core/auth/auth.service';
import { InvestmentService } from '../../core/investment/investment.service';
import { LanguageService } from '../../core/i18n/language.service';

/**
 * 005-search-ui-fixes:
 *  - US2 (currency label): the currency <option>s must render the localized label via the
 *    `igCurrency` pipe (UAH -> "грн" in Ukrainian, "UAH" in English; USD stays "USD"), updating
 *    live on language switch, while the form control value stays the canonical code.
 *  - US3 (form width): the form must no longer be pinned to a fixed left-column width.
 */
describe('SearchComponent (US2 currency label, US3 form width)', () => {
  let fixture: ComponentFixture<SearchComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SearchComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { tokenBalance: signal(5) } },
        { provide: InvestmentService, useValue: { search: () => of() } },
        { provide: LanguageService, useValue: { current: signal('uk') } },
      ],
    }).compileComponents();

    const translate = TestBed.inject(TranslateService);
    // Only the currency labels matter for these assertions; other keys render as their key strings.
    translate.setTranslation('uk', { currency: { UAH: 'грн', USD: 'USD' } });
    translate.setTranslation('en', { currency: { UAH: 'UAH', USD: 'USD' } });
    translate.use('uk');

    fixture = TestBed.createComponent(SearchComponent);
    fixture.detectChanges();
  });

  function optionText(value: string): string {
    const opts = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('#currency option'),
    ) as HTMLOptionElement[];
    return opts.find((o) => o.value === value)?.textContent?.trim() ?? '';
  }

  it('renders the hryvnia option as "грн" in Ukrainian', () => {
    expect(optionText('UAH')).toBe('грн');
  });

  it('renders the hryvnia option as "UAH" in English', () => {
    TestBed.inject(TranslateService).use('en');
    fixture.detectChanges();
    expect(optionText('UAH')).toBe('UAH');
  });

  it('updates the currency label live when the language switches (no reload)', () => {
    expect(optionText('UAH')).toBe('грн');
    TestBed.inject(TranslateService).use('en');
    fixture.detectChanges();
    expect(optionText('UAH')).toBe('UAH');
    TestBed.inject(TranslateService).use('uk');
    fixture.detectChanges();
    expect(optionText('UAH')).toBe('грн');
  });

  it('keeps USD labelled "USD" in both languages (no over-localization)', () => {
    expect(optionText('USD')).toBe('USD');
    TestBed.inject(TranslateService).use('en');
    fixture.detectChanges();
    expect(optionText('USD')).toBe('USD');
  });

  it('keeps the form control value as the canonical currency CODE, not the label', () => {
    // Localizing the visible label must not change the submitted value (FR-007).
    expect(fixture.componentInstance.form.controls.currency.value).toBe('UAH');
    fixture.componentInstance.form.controls.currency.setValue('USD');
    expect(fixture.componentInstance.form.controls.currency.value).toBe('USD');
  });

  it('does not pin the search form to a fixed left-column max-width (US3)', () => {
    const form = (fixture.nativeElement as HTMLElement).querySelector(
      'form.ig-form--wide',
    ) as HTMLElement;
    expect(form).toBeTruthy();
    const maxW = getComputedStyle(form).maxWidth;
    // Positively assert the cap is removed so the form fills the card (not merely "changed").
    // 'none' also guarantees it is neither the old component cap (560px) nor the global .ig-form
    // fallback (420px), which would re-introduce the crammed-left defect.
    expect(maxW).toBe('none');
  });
});
