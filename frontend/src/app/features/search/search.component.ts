import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { InvestmentService } from '../../core/investment/investment.service';
import { SearchRequestBody, SearchResponse } from '../../core/investment/investment.models';
import { toMinorUnits } from '../../core/investment/money.util';
import { parseApiError } from '../../core/api/api-error.util';
import { LanguageService } from '../../core/i18n/language.service';
import { PluralPipe } from '../../core/i18n/plural.pipe';
import { CurrencyLabelPipe } from '../../core/i18n/currency-label.pipe';
import { ResultsComponent } from './results.component';

/**
 * Investment search page (tickets FE-SEARCH1/2/3, §4.2, §5.2, §5.4).
 *
 * Form validation mirrors §5.2 (amount > 0; goals <= 280). The upper bound on amount is
 * server-authoritative ([CONFIG] search.maxAmount), so it is NOT hard-coded here - an over-limit
 * amount surfaces the backend VALIDATION_ERROR inline (FE-SEARCH1). Amount is entered in UAH/USD and
 * converted to minor units before submit. Submit is disabled at 0 tokens and links to Buy Tokens. The
 * error states map 402/502/429/403/empty per FE-SEARCH3, and the balance shown never changes on a
 * failed/refunded search (no client-side crediting).
 *
 * The current UI language is sent as `language` so the advisor returns instrument/rationale text in
 * Ukrainian or English to match what the user is reading (i18n; LLM-output localization).
 */
@Component({
  selector: 'ig-search',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, ResultsComponent, DecimalPipe, TranslateModule, PluralPipe, CurrencyLabelPipe],
  template: `
    <section class="ig-card ig-search__head reveal d1">
      <p class="ig-kicker">{{ 'search.kicker' | translate }}</p>
      <h1 class="ig-display">{{ 'search.title' | translate }}</h1>
      <p class="ig-muted">{{ 'search.intro' | translate: { balance: (balance() | igPlural: 'token') } }}</p>

      @if (balance() === 0) {
        <div class="ig-alert ig-alert--info">
          {{ 'search.outOfTokens' | translate }} <a routerLink="/tokens">{{ 'search.buyMore' | translate }}</a> {{ 'search.buyMoreSuffix' | translate }}
        </div>
      }

      <form class="ig-form ig-form--wide" [formGroup]="form" (ngSubmit)="submit()" novalidate
            [attr.aria-busy]="submitting() ? 'true' : null">
        <div class="ig-grid2">
          <div class="ig-field">
            <label for="amount">{{ 'search.amount' | translate }} <span class="ig-req" aria-hidden="true">*</span></label>
            <input id="amount" type="number" inputmode="decimal" step="0.01" min="0.01"
                   formControlName="amount" required
                   [attr.aria-invalid]="showError('amount') ? 'true' : null"
                   [attr.aria-describedby]="showError('amount') ? 'amount-error' : null" />
            @if (showError('amount')) {
              <span id="amount-error" class="ig-error" role="alert">{{ 'search.amountError' | translate }}</span>
            }
          </div>
          <div class="ig-field">
            <label for="currency">{{ 'search.currency' | translate }}</label>
            <select id="currency" class="ig-select" formControlName="currency">
              <option value="UAH">{{ 'UAH' | igCurrency }}</option>
              <option value="USD">{{ 'USD' | igCurrency }}</option>
            </select>
          </div>
        </div>

        <div class="ig-grid2">
          <div class="ig-field">
            <label for="horizon">{{ 'search.horizon' | translate }}</label>
            <select id="horizon" class="ig-select" formControlName="horizon">
              <option [ngValue]="null">{{ 'search.any' | translate }}</option>
              <option value="SHORT">{{ 'search.horizonShort' | translate }}</option>
              <option value="MEDIUM">{{ 'search.horizonMedium' | translate }}</option>
              <option value="LONG">{{ 'search.horizonLong' | translate }}</option>
            </select>
          </div>
          <div class="ig-field">
            <label for="risk">{{ 'search.risk' | translate }}</label>
            <select id="risk" class="ig-select" formControlName="riskTolerance">
              <option [ngValue]="null">{{ 'search.any' | translate }}</option>
              <option value="LOW">{{ 'search.riskLow' | translate }}</option>
              <option value="MODERATE">{{ 'search.riskModerate' | translate }}</option>
              <option value="HIGH">{{ 'search.riskHigh' | translate }}</option>
            </select>
          </div>
        </div>

        <div class="ig-field">
          <label for="goals">{{ 'search.goals' | translate }}</label>
          <textarea id="goals" rows="2" maxlength="280" formControlName="goals"
                    aria-describedby="goals-count"
                    [placeholder]="'search.goalsPlaceholder' | translate"></textarea>
          <span id="goals-count" class="ig-hint">{{ goalsLength() }}/280</span>
        </div>

        @if (errorMessage()) {
          <div class="ig-alert ig-alert--error">
            {{ errorMessage() }}
            @if (showBuyLink()) {
              <a routerLink="/tokens">{{ 'search.buyTokens' | translate }}</a>.
            }
            @if (showVerifyLink()) {
              <a routerLink="/verify">{{ 'search.verifyEmail' | translate }}</a>.
            }
          </div>
        }

        <button type="submit" class="ig-btn ig-btn--lg" [disabled]="submitting() || balance() === 0">
          {{ (submitting() ? 'search.submitting' : 'search.submit') | translate }}
        </button>
      </form>
    </section>

    <!-- Polite live region: announces the result summary only on a successful render, so it does
         not double-fire with the assertive amount-field error (feature 007, FR-003 / C-A11Y-4). -->
    <p class="ig-sr-only" role="status" aria-live="polite">{{ resultsAnnouncement() }}</p>

    @if (result()) {
      <section class="ig-card ig-results-wrap">
        <h2>{{ 'search.optionsFor' | translate: { amount: (result()!.amount / 100 | number: '1.2-2'), currency: (result()!.currency | igCurrency) } }}</h2>
        <ig-results [result]="result()!" />
      </section>
    }
  `,
  styles: [
    `
      .ig-grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
      /* Collapse to one column a bit earlier (was 520px) so the two-up fields are not cramped in
         the 560-640px tablet range flagged in the audit (feature 007, FR-011). */
      @media (max-width: 640px) { .ig-grid2 { grid-template-columns: 1fr; } }
      /* Fill the card width. This is an explicit override (not a deletion): the global
         .ig-form rule caps width at 420px, so removing this line would make the form narrower
         and re-introduce the "crammed left" defect (005-search-ui-fixes US3). The .ig-grid2
         1fr/1fr columns then distribute the width evenly; the 520px rule keeps mobile legible. */
      .ig-form--wide { max-width: none; }
      .ig-search__head h1 { margin-top: 0; }
    `,
  ],
})
export class SearchComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly investments = inject(InvestmentService);
  private readonly translate = inject(TranslateService);
  private readonly lang = inject(LanguageService);

  readonly balance = computed(() => this.auth.tokenBalance());

  readonly form = this.fb.nonNullable.group({
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    currency: ['UAH' as 'UAH' | 'USD', [Validators.required]],
    horizon: [null as string | null],
    riskTolerance: [null as string | null],
    goals: ['', [Validators.maxLength(280)]],
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showBuyLink = signal(false);
  readonly showVerifyLink = signal(false);
  readonly result = signal<SearchResponse | null>(null);
  /** Screen-reader announcement set only when results successfully render (FR-003). */
  readonly resultsAnnouncement = signal('');

  goalsLength(): number {
    return (this.form.controls.goals.value ?? '').length;
  }

  showError(control: 'amount'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.touched || c.dirty);
  }

  submit(): void {
    this.errorMessage.set(null);
    this.showBuyLink.set(false);
    this.showVerifyLink.set(false);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body: SearchRequestBody = {
      amount: toMinorUnits(Number(raw.amount)),
      currency: raw.currency,
      horizon: (raw.horizon as SearchRequestBody['horizon']) ?? null,
      riskTolerance: (raw.riskTolerance as SearchRequestBody['riskTolerance']) ?? null,
      goals: raw.goals?.trim() ? raw.goals.trim() : null,
      // Tell the advisor which language to write instrument/rationale text in (UK | EN).
      language: this.lang.current().toUpperCase() as SearchRequestBody['language'],
    };

    this.submitting.set(true);
    this.result.set(null);
    this.resultsAnnouncement.set('');
    // Lock the field group while the search is in flight (FR-020); re-enabled on settle below.
    this.form.disable({ emitEvent: false });
    this.investments.search(body).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.form.enable({ emitEvent: false });
        this.result.set(res);
        this.resultsAnnouncement.set(
          this.translate.instant('search.resultsAnnounce', { count: res.options.length }),
        );
      },
      error: (err) => {
        this.submitting.set(false);
        this.form.enable({ emitEvent: false });
        this.mapError(err);
      },
    });
  }

  /** FE-SEARCH3 error UX. Balance is never mutated here - the server already refunded on failure. */
  private mapError(err: unknown): void {
    const parsed = parseApiError(err);
    switch (parsed.code) {
      case 'INSUFFICIENT_TOKENS':
        this.errorMessage.set(this.translate.instant('search.errOutOfTokens'));
        this.showBuyLink.set(true);
        break;
      case 'ADVISOR_UNAVAILABLE':
        this.errorMessage.set(this.translate.instant('search.errAdvisorBusy'));
        break;
      case 'RATE_LIMITED':
        this.errorMessage.set(this.translate.instant('search.errRateLimited'));
        break;
      case 'EMAIL_NOT_VERIFIED':
        this.errorMessage.set(this.translate.instant('search.errEmailNotVerified'));
        this.showVerifyLink.set(true);
        break;
      case 'VALIDATION_ERROR':
        this.errorMessage.set(parsed.message || this.translate.instant('search.errValidation'));
        break;
      default:
        this.errorMessage.set(parsed.message);
    }
  }
}
