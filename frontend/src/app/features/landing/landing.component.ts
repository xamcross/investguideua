import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Guest landing page (ticket FE-AUTH0, §3 Guest role, §12 Landing). Product summary, how it works,
 * and Register/Login CTAs. Makes no authenticated API calls. Authenticated users are redirected to
 * /search.
 */
@Component({
  selector: 'ig-landing',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule],
  template: `
    <section class="ig-hero" aria-labelledby="ig-hero-title">
      <div class="ig-hero__copy reveal d1">
        <p class="ig-eyebrow">{{ 'landing.eyebrow' | translate }}</p>
        <h1 id="ig-hero-title" class="ig-hero__title">
          {{ 'landing.titleLead' | translate }}
          <span class="ig-l-accent">{{ 'landing.titleAccent' | translate }}</span>
        </h1>
        <p class="ig-lead">{{ 'landing.lead' | translate }}</p>
        <div class="ig-cta">
          <a routerLink="/register" class="ig-btn ig-btn--gold ig-btn--lg">{{ 'landing.ctaStart' | translate }}</a>
          <a routerLink="/login" class="ig-btn ig-btn--ghost ig-btn--lg">{{ 'landing.ctaSignIn' | translate }}</a>
        </div>
        <p class="ig-hint ig-hero__hint">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor"
               stroke-width="2.5" aria-hidden="true" focusable="false"><path d="M3 8.5l3.5 3.5L13 4"/></svg>
          {{ 'landing.hint' | translate }}
        </p>
      </div>

      <aside class="ig-ledger reveal d2" [attr.aria-label]="'landing.ledgerLabel' | translate">
        <span class="ig-ledger__eyebrow">{{ 'landing.ledgerLabel' | translate }}</span>
        <div class="ig-ledger__row">
          <span class="ig-ledger__name">{{ 'landing.ledgerDeposit' | translate }}</span>
          <span class="ig-ledger__fig">14.5&ndash;16.0%</span>
        </div>
        <div class="ig-ledger__row">
          <span class="ig-ledger__name">{{ 'landing.ledgerBond' | translate }}</span>
          <span class="ig-ledger__fig">17.0&ndash;19.5%</span>
        </div>
        <div class="ig-ledger__row">
          <span class="ig-ledger__name">{{ 'landing.ledgerFund' | translate }}</span>
          <span class="ig-ledger__fig">12.0&ndash;14.0%</span>
        </div>
        <div class="ig-ledger__amount">25&thinsp;000 <span class="ig-ledger__cur">{{ 'landing.ledgerCurrency' | translate }}</span></div>
        <p class="ig-ledger__foot">{{ 'landing.ledgerFoot' | translate }}</p>
      </aside>
    </section>

    <hr class="ig-flagrule" aria-hidden="true" />

    <section class="ig-how" aria-labelledby="ig-how-title">
      <p class="ig-kicker">{{ 'landing.howKicker' | translate }}</p>
      <h2 id="ig-how-title">{{ 'landing.howTitle' | translate }}</h2>
      <ol class="ig-steps">
        <li class="ig-card ig-step reveal d2">
          <span class="ig-step__num" aria-hidden="true">01</span>
          <h3>{{ 'landing.step1Title' | translate }}</h3>
          <p class="ig-muted">{{ 'landing.step1Body' | translate }}</p>
        </li>
        <li class="ig-card ig-step reveal d3">
          <span class="ig-step__num" aria-hidden="true">02</span>
          <h3>{{ 'landing.step2Title' | translate }}</h3>
          <p class="ig-muted">{{ 'landing.step2Body' | translate }}</p>
        </li>
        <li class="ig-card ig-step reveal d4">
          <span class="ig-step__num" aria-hidden="true">03</span>
          <h3>{{ 'landing.step3Title' | translate }}</h3>
          <p class="ig-muted">{{ 'landing.step3Body' | translate }}</p>
        </li>
      </ol>
    </section>

    <p class="ig-l-disclaimer">
      <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor"
           stroke-width="2" aria-hidden="true" focusable="false">
        <circle cx="8" cy="8" r="6.5"/><path d="M8 7.2v4M8 4.8h.01"/></svg>
      <span>{{ 'landing.disclaimer' | translate }}</span>
    </p>
  `,
  styles: [
    `
      .ig-hero { display: grid; grid-template-columns: 1.25fr 1fr; gap: 2rem; align-items: center; padding: 1.5rem 0 2rem; }
      .ig-hero__title { font-size: clamp(2.1rem, 4.2vw, 3.1rem); }
      .ig-l-accent { font-style: italic; color: var(--blue-600); position: relative; white-space: nowrap; }
      .ig-l-accent::after { content: ""; position: absolute; left: -2px; right: -2px; bottom: .08em; height: .32em;
        background: var(--gold-300); opacity: .55; transform: skewX(-12deg); z-index: -1; border-radius: 2px; }
      .ig-lead { font-size: 1.12rem; max-width: 46ch; margin: 0 0 1.5rem; color: var(--muted); }
      .ig-cta { display: flex; gap: .75rem; flex-wrap: wrap; }
      .ig-hero__hint { display: inline-flex; align-items: center; gap: .4rem; margin-top: 1rem; color: var(--gold-700); }
      .ig-ledger { position: relative; overflow: hidden; border-radius: var(--radius); padding: 1.5rem;
        background: radial-gradient(120% 90% at 90% -10%, rgba(217,168,35,.16), transparent 55%), var(--navy-900);
        color: rgba(255,255,255,.74); box-shadow: var(--shadow-lg); }
      .ig-ledger::before { content: ""; position: absolute; top: 0; right: 0; width: 70px; height: 3px; background: var(--gold-500); }
      .ig-ledger__eyebrow { font-family: var(--font-mono); font-size: .66rem; font-weight: 700; letter-spacing: .14em;
        text-transform: uppercase; color: var(--gold-300); }
      .ig-ledger__row { display: flex; justify-content: space-between; align-items: baseline; gap: 1rem;
        padding: .55rem 0; border-bottom: 1px solid rgba(255,255,255,.1); }
      .ig-ledger__name { font-size: .9rem; }
      .ig-ledger__fig { font-family: var(--font-mono); font-weight: 700; color: var(--gold-300); }
      .ig-ledger__amount { font-family: var(--font-display); font-weight: 800; font-size: 2rem; color: #fff; margin-top: 1rem; }
      .ig-ledger__cur { font-family: var(--font-mono); font-size: .8rem; font-weight: 700; color: var(--blue-300); }
      .ig-ledger__foot { font-size: .76rem; color: rgba(255,255,255,.6); margin: .5rem 0 0; }
      .ig-flagrule { height: 4px; border: 0; margin: 1.5rem 0; border-radius: 2px;
        background: linear-gradient(90deg, var(--blue-600) 0 50%, var(--gold-500) 50% 100%); }
      .ig-how { padding: 1rem 0 1.5rem; }
      .ig-steps { list-style: none; padding: 0; margin: 1.25rem 0 0;
        display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; }
      .ig-step { position: relative; padding-left: 1.5rem; }
      .ig-step::before { content: ""; position: absolute; left: 0; top: 1.5rem; bottom: 1.5rem; width: 4px; border-radius: 2px;
        background: linear-gradient(180deg, var(--blue-600), var(--gold-500)); transform: scaleY(.4); transform-origin: top; transition: transform .35s var(--ease); }
      .ig-step:hover::before { transform: scaleY(1); }
      .ig-step__num { font-family: var(--font-display); font-style: italic; font-weight: 700; font-size: 1.6rem; color: var(--gold-600); display: block; margin-bottom: .25rem; }
      .ig-step h3 { margin: .15rem 0; }
      .ig-l-disclaimer { display: flex; gap: .6rem; align-items: flex-start; max-width: 70ch;
        margin: 1.5rem 0 0; padding: .85rem 1rem; border-left: 4px solid var(--gold-500);
        background: var(--surface-2); border-radius: var(--radius-sm); color: var(--muted); font-size: .85rem; }
      .ig-l-disclaimer svg { flex: none; margin-top: .15rem; color: var(--gold-700); }
      @media (max-width: 900px) {
        .ig-hero { grid-template-columns: 1fr; }
        .ig-steps { grid-template-columns: 1fr; }
      }
      @media (prefers-reduced-motion: reduce) {
        .ig-step::before { transition: none; transform: scaleY(1); }
      }
    `,
  ],
})
export class LandingComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      void this.router.navigate(['/search']);
    }
  }
}
