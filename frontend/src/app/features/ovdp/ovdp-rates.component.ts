import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { StructuredDataService } from '../../core/seo/structured-data.service';
import { formatMinorUnits } from '../../core/investment/money.util';
import { BondSnapshotService } from './bond-snapshot.service';
import { BondSnapshot } from './bond-snapshot.data';

/**
 * /ovdp/rates (feature 015): live per-issue bond quotes. Prerenders entirely from the build-time
 * snapshot (crawlers and no-JS readers always see the table + as-of stamp); in the browser it
 * refreshes once from the public API, silently keeping the snapshot on failure. Empty data renders
 * an explicit empty state - never fabricated rows (audit A30: data-ovdp-asof OR data-ovdp-empty).
 */
@Component({
  selector: 'ig-ovdp-rates',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule, DatePipe],
  template: `
    <section class="ig-ovdp" aria-labelledby="ig-ovdp-title">
      <p class="ig-kicker">{{ 'ovdp.rates.kicker' | translate }}</p>
      <h1 id="ig-ovdp-title">{{ 'ovdp.rates.title' | translate }}</h1>
      <p class="ig-lead">{{ 'ovdp.rates.intro' | translate }}</p>

      @if (snap().bonds.length) {
        <p class="ig-ovdp__asof" data-ovdp-asof>
          {{ 'ovdp.asOf' | translate }} {{ (snap().fetchedAt | date: 'dd.MM.yyyy HH:mm') || '-' }}
        </p>
        <div class="ig-ovdp__scroll">
          <table class="ig-ovdp__table">
            <thead>
              <tr>
                <th scope="col">{{ 'ovdp.rates.colIsin' | translate }}</th>
                <th scope="col">{{ 'ovdp.rates.colType' | translate }}</th>
                <th scope="col">{{ 'ovdp.rates.colCurrency' | translate }}</th>
                <th scope="col">{{ 'ovdp.rates.colMaturity' | translate }}</th>
                <th scope="col" class="ig-num">{{ 'ovdp.rates.colPrice' | translate }}</th>
                <th scope="col" class="ig-num">{{ 'ovdp.rates.colYield' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (b of snap().bonds; track b.isin) {
                <tr>
                  <td class="ig-ovdp__isin">{{ b.isin }}</td>
                  <td>
                    <span class="ig-badge" [class.ig-badge--mil]="b.military">
                      {{ (b.military ? 'ovdp.rates.typeMilitary' : 'ovdp.rates.typeRegular') | translate }}
                    </span>
                  </td>
                  <td>{{ b.currency }}</td>
                  <td>{{ b.maturity | date: 'dd.MM.yyyy' }}</td>
                  <td class="ig-num">{{ price(b.sellPriceMinor, b.currency) }}</td>
                  <td class="ig-num"><b>{{ b.sellYield }}%</b></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <p class="ig-ovdp__empty" data-ovdp-empty>{{ 'ovdp.empty' | translate }}</p>
      }

      <div class="ig-ovdp__links">
        <a routerLink="/ovdp/calculator" class="ig-btn ig-btn--primary">{{ 'ovdp.rates.linkCalc' | translate }}</a>
        <a routerLink="/ovdp/vs-deposit" class="ig-btn ig-btn--ghost">{{ 'ovdp.rates.linkVs' | translate }}</a>
      </div>

      <h2>{{ 'ovdp.rates.h2How' | translate }}</h2>
      <p>{{ 'ovdp.rates.howBody' | translate }}</p>

      <h2>{{ 'ovdp.rates.h2Where' | translate }}</h2>
      <p>{{ 'ovdp.rates.whereBody' | translate }}</p>
      <p><a routerLink="/articles/ovdp-war-bonds">{{ 'ovdp.rates.linkGuide' | translate }}</a></p>

      <section class="ig-ovdp__faq" data-faq-block>
        @for (f of faq(); track f.q) {
          <h3>{{ f.q }}</h3>
          <p>{{ f.a }}</p>
        }
      </section>

      <p class="ig-ovdp__disclaimer">{{ 'ovdp.disclaimer' | translate }}</p>
    </section>
  `,
  styles: [
    `
      .ig-ovdp { max-width: 72ch; margin: 0 auto; padding: 1.5rem 0 2.5rem; }
      .ig-ovdp h1 { font-size: clamp(1.8rem, 3.4vw, 2.4rem); }
      .ig-ovdp h2 { margin-top: 2rem; }
      .ig-ovdp__asof { font-family: var(--font-mono); font-size: .8rem; color: var(--muted); }
      .ig-ovdp__scroll { overflow-x: auto; }
      .ig-ovdp__table { width: 100%; border-collapse: collapse; font-size: .9rem; }
      .ig-ovdp__table th, .ig-ovdp__table td { padding: .55rem .6rem; border-bottom: 1px solid var(--surface-2); text-align: left; }
      .ig-ovdp__table .ig-num { text-align: right; white-space: nowrap; }
      .ig-ovdp__isin { font-family: var(--font-mono); font-size: .82rem; }
      .ig-badge--mil { background: var(--gold-500); color: var(--navy-900); }
      .ig-ovdp__empty { padding: 1rem; border-left: 4px solid var(--gold-500); background: var(--surface-2); }
      .ig-ovdp__links { display: flex; gap: .75rem; flex-wrap: wrap; margin: 1.25rem 0; }
      .ig-ovdp__faq h3 { margin: 1.25rem 0 .35rem; font-size: 1rem; }
      .ig-ovdp__faq p { margin: 0; color: var(--muted); }
      .ig-ovdp__disclaimer { margin-top: 2rem; padding: .85rem 1rem; border-left: 4px solid var(--gold-500);
        background: var(--surface-2); border-radius: var(--radius-sm); color: var(--muted); font-size: .85rem; }
    `,
  ],
})
export class OvdpRatesComponent implements OnInit, OnDestroy {
  private readonly snapshots = inject(BondSnapshotService);
  private readonly structuredData = inject(StructuredDataService);
  private readonly translate = inject(TranslateService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly snap = signal<BondSnapshot>(this.snapshots.snapshot());
  protected readonly faq = signal<readonly { q: string; a: string }[]>([]);

  ngOnInit(): void {
    // FAQ copy + JSON-LD from i18n; re-resolved on language switch like ContentPageComponent.
    // takeUntilDestroyed: a zombie subscription would re-inject this page's JSON-LD onto whatever
    // route the user is on at the next language switch.
    this.loadFaqAndJsonLd();
    this.translate.onLangChange
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadFaqAndJsonLd());

    // Browser-only refresh; prerender must stay network-free and a failure keeps the snapshot.
    if (isPlatformBrowser(this.platformId)) {
      this.snapshots.latest().subscribe({
        next: (s) => { if (s.bonds.length) this.snap.set(s); },
        error: () => {},
      });
    }
  }

  ngOnDestroy(): void {
    this.structuredData.clearPageBlocks();
  }

  protected price(minor: number, currency: string): string {
    return formatMinorUnits(minor, currency);
  }

  private loadFaqAndJsonLd(): void {
    this.translate.get(['ovdp.rates.faq', 'seo.ovdpRates.title', 'seo.ovdpRates.desc']).subscribe((t) => {
      const faq: { q: string; a: string }[] = Array.isArray(t['ovdp.rates.faq']) ? t['ovdp.rates.faq'] : [];
      this.faq.set(faq);
      this.structuredData.setPageBlocks([
        {
          '@context': 'https://schema.org',
          '@type': 'Dataset',
          name: t['seo.ovdpRates.title'],
          description: t['seo.ovdpRates.desc'],
          url: this.structuredData.pageUrl('/ovdp/rates'),
          inLanguage: 'uk',
          isAccessibleForFree: true,
          dateModified: this.snap().fetchedAt ?? undefined,
          creator: { '@id': this.structuredData.organizationId },
        },
        ...(faq.length
          ? [{
              '@context': 'https://schema.org',
              '@type': 'FAQPage',
              mainEntity: faq.map((f) => ({
                '@type': 'Question',
                name: f.q,
                acceptedAnswer: { '@type': 'Answer', text: f.a },
              })),
            }]
          : []),
      ]);
    });
  }
}
