import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { InvestmentService } from '../../core/investment/investment.service';
import { SearchRequestBody, SearchResponse } from '../../core/investment/investment.models';
import { toMinorUnits } from '../../core/investment/money.util';
import { parseApiError } from '../../core/api/api-error.util';
import { LanguageService } from '../../core/i18n/language.service';
import { PluralPipe } from '../../core/i18n/plural.pipe';
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
  imports: [ReactiveFormsModule, RouterLink, ResultsComponent, DecimalPipe, TranslatePipe, PluralPipe],
  template: `
    <section class="ig-card">
      <h1>{{ 'search.title' | translate }}</h1>
      <p class="ig-muted">{{ 'search.intro' | translate: { balance: (balance() | igPlural: 'token') } }}</p>

      @if (balance() === 0) {
        <div class="ig-alert ig-alert--info">
          {{ 'search.outOfTokens' | translate }} <a routerLink="/tokens">{{ 'search.buyMore' | translate }}</a> {{ 'search.buyMoreSuffix' | translate }}
        </div>
      }

      <form class="ig-form" [formGroup]="form" (ngSubmit)="submit()">
        <div class="ig-grid2">
          <div class="ig-field">
            <label for="amount">{{ 'search.amount' | translate }}</label>
            <input id="amount" type="number" inputmode="decimal" step="0.01" min="0.01"
                   formControlName="amount" />
            @if (showError('amount')) {
              <span class="ig-error">{{ 'search.amountError' | translate }}</span>
            }
          </div>
          <div class="ig-field">
            <label for="currency">{{ 'search.currency' | translate }}</label>
            <select id="currency" formControlName="currency">
              <option value="UAH">UAH</option>
              <option value="USD">USD</option>
            </select>
          </div>
        </div>

        <div class="ig-grid2">
          <div class="ig-field">
            <label for="horizon">{{ 'search.horizon' | translate }}</label>
            <select id="horizon" formControlName="horizon">
              <option [ngValue]="null">{{ 'search.any' | translate }}</option>
              <option value="SHORT">{{ 'search.horizonShort' | translate }}</option>
              <option value="MEDIUM">{{ 'search.horizonMedium' | translate }}</option>
              <option value="LONG">{{ 'search.horizonLong' | translate }}</option>
            </select>
          </div>
          <div class="ig-field">
            <label for="risk">{{ 'search.risk' | translate }}</label>
            <select id="risk" formControlName="riskTolerance">
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
                    [placeholder]="'search.goalsPlaceholder' | translate"></textarea>
          <span class="ig-hint">{{ goalsLength() }}/280</span>
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

        <button type="submit" class="ig-btn" [disabled]="submitting() || balance() === 0">
          {{ (submitting() ? 'search.submitting' : 'search.submit') | translate }}
        </button>
      </form>
    </section>

    @if (result()) {
      <section class="ig-card">
        <h2>{{ 'search.optionsFor' | translate: { amount: (result()!.amount / 100 | number: '1.2-2'), currency: result()!.currency } }}</h2>
        <ig-results [result]="result()!" />
      </section>
    }
  `,
  styles: [
    `
      .ig-grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
      @media (max-width: 520px) { .ig-grid2 { grid-template-columns: 1fr; } }
      textarea { width: 100%; box-sizing: border-box; font: inherit; padding: 0.5rem; border: 1px solid var(--ig-border); border-radius: 8px; }
      select { width: 100%; box-sizing: border-box; font: inherit; padding: 0.5rem; border: 1px solid var(--ig-border); border-radius: 8px; background: #fff; }
      .ig-alert--info { background: rgba(0, 87, 183, 0.06); border: 1px solid var(--ig-border); border-radius: 8px; padding: 0.75rem 1rem; margin-bottom: 1rem; }
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
    this.investments.search(body).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.result.set(res);
      },
      error: (err) => {
        this.submitting.set(false);
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
