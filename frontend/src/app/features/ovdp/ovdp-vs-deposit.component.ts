import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { DatePipe, isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { StructuredDataService } from '../../core/seo/structured-data.service';
import { formatMinorUnits, toMinorUnits } from '../../core/investment/money.util';
import { BondSnapshotService } from './bond-snapshot.service';
import { BondSnapshot } from './bond-snapshot.data';
import { depositNetKop, grossIncomeKop, ovdpNetKop } from './ovdp-math';

/**
 * /ovdp/vs-deposit (feature 015). Same amount + term through both instruments: ОВДП net equals
 * gross (tax-exempt), the deposit loses 23% withholding. Default rates seed from the snapshot's
 * median sell yield (both sides equal, so the delta isolates the TAX effect, not a rate guess);
 * with an empty snapshot both default to 16%. Funnel CTA into /search. Integer kopiyka math.
 */
@Component({
  selector: 'ig-ovdp-vs-deposit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule, DatePipe],
  template: `
    <section class="ig-ovdp ig-vs" aria-labelledby="ig-vs-title">
      <p class="ig-kicker">{{ 'ovdp.vs.kicker' | translate }}</p>
      <h1 id="ig-vs-title">{{ 'ovdp.vs.title' | translate }}</h1>
      <p class="ig-lead">{{ 'ovdp.vs.intro' | translate }}</p>

      @if (snap().bonds.length) {
        <p class="ig-ovdp__asof" data-ovdp-asof>
          {{ 'ovdp.asOf' | translate }} {{ snap().fetchedAt | date: 'dd.MM.yyyy HH:mm' }}
        </p>
      } @else {
        <p class="ig-ovdp__empty" data-ovdp-empty>{{ 'ovdp.empty' | translate }}</p>
      }

      <div class="ig-calc__form">
        <label>
          <span>{{ 'ovdp.vs.amount' | translate }}</span>
          <input type="number" min="0" step="1000" [value]="amountUah()"
                 (input)="amountUah.set(num($event, 50000))" />
        </label>
        <label>
          <span>{{ 'ovdp.vs.term' | translate }}</span>
          <input type="number" min="1" max="60" step="1" [value]="termMonths()"
                 (input)="termMonths.set(num($event, 12))" />
        </label>
        <label>
          <span>{{ 'ovdp.vs.ovdpRate' | translate }}</span>
          <input type="number" min="0" max="99" step="0.1" [value]="ovdpRate()"
                 (input)="ovdpRate.set(num($event, 16))" />
        </label>
        <label>
          <span>{{ 'ovdp.vs.depositRate' | translate }}</span>
          <input type="number" min="0" max="99" step="0.1" [value]="depositRate()"
                 (input)="depositRate.set(num($event, 16))" />
        </label>
      </div>

      <div class="ig-vs__panels">
        <div class="ig-vs__panel ig-vs__panel--ovdp">
          <h2>{{ 'ovdp.vs.ovdpPanel' | translate }}</h2>
          <p class="ig-vs__net"><b>{{ money(ovdpNet()) }}</b></p>
          <p class="ig-vs__note">{{ 'ovdp.vs.netLabel' | translate }} - {{ 'ovdp.vs.taxFreeNote' | translate }}</p>
        </div>
        <div class="ig-vs__panel">
          <h2>{{ 'ovdp.vs.depositPanel' | translate }}</h2>
          <p class="ig-vs__net">{{ money(depositNet()) }}</p>
          <p class="ig-vs__note">{{ 'ovdp.vs.netLabel' | translate }} - {{ 'ovdp.vs.taxNote' | translate }}</p>
        </div>
      </div>

      <p class="ig-vs__delta">
        @if (delta() >= 0) {
          {{ 'ovdp.vs.deltaPrefix' | translate }} <b>{{ money(delta()) }}</b> {{ 'ovdp.vs.deltaSuffix' | translate }}
        } @else {
          {{ 'ovdp.vs.deltaNegativePrefix' | translate }} <b>{{ money(-delta()) }}</b> {{ 'ovdp.vs.deltaNegativeSuffix' | translate }}
        }
      </p>

      <h2>{{ 'ovdp.vs.h2Why' | translate }}</h2>
      <p>{{ 'ovdp.vs.whyBody' | translate }}</p>

      <div class="ig-vs__cta">
        <h2>{{ 'ovdp.vs.ctaTitle' | translate }}</h2>
        <p>{{ 'ovdp.vs.ctaBody' | translate }}</p>
        <a routerLink="/search" class="ig-btn ig-btn--gold">{{ 'ovdp.vs.cta' | translate }}</a>
      </div>

      <div class="ig-ovdp__links">
        <a routerLink="/ovdp/rates" class="ig-btn ig-btn--ghost">{{ 'ovdp.vs.linkRates' | translate }}</a>
        <a routerLink="/ovdp/calculator" class="ig-btn ig-btn--ghost">{{ 'ovdp.vs.linkCalc' | translate }}</a>
      </div>

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
      .ig-ovdp__empty { padding: 1rem; border-left: 4px solid var(--gold-500); background: var(--surface-2); }
      .ig-ovdp__links { display: flex; gap: .75rem; flex-wrap: wrap; margin: 1.25rem 0; }
      .ig-ovdp__faq h3 { margin: 1.25rem 0 .35rem; font-size: 1rem; }
      .ig-ovdp__faq p { margin: 0; color: var(--muted); }
      .ig-ovdp__disclaimer { margin-top: 2rem; padding: .85rem 1rem; border-left: 4px solid var(--gold-500);
        background: var(--surface-2); border-radius: var(--radius-sm); color: var(--muted); font-size: .85rem; }
      .ig-calc__form { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; margin: 1.25rem 0; }
      .ig-calc__form label { display: flex; flex-direction: column; gap: .35rem; font-size: .85rem; color: var(--muted); }
      .ig-calc__form input { padding: .55rem .6rem; border: 1px solid var(--surface-2); border-radius: var(--radius-sm); font: inherit; }
      .ig-vs__panels { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
      .ig-vs__panel { border: 1px solid var(--surface-2); border-radius: var(--radius-sm); padding: 1rem 1.25rem; }
      .ig-vs__panel--ovdp { border-left: 4px solid var(--gold-500); }
      .ig-vs__panel h2 { margin: 0 0 .5rem; font-size: 1rem; }
      .ig-vs__net { font-size: 1.4rem; margin: 0; }
      .ig-vs__note { color: var(--muted); font-size: .82rem; margin: .25rem 0 0; }
      .ig-vs__delta { margin: 1.25rem 0; padding: .85rem 1rem; background: var(--surface-2); border-radius: var(--radius-sm); }
      .ig-vs__cta { border: 1px solid var(--surface-2); border-radius: var(--radius-sm); padding: 1rem 1.25rem; margin-top: 1.5rem; }
      .ig-vs__cta h2 { margin: 0 0 .5rem; font-size: 1.05rem; }
      @media (max-width: 640px) { .ig-vs__panels { grid-template-columns: 1fr; } }
    `,
  ],
})
export class OvdpVsDepositComponent implements OnInit, OnDestroy {
  private readonly snapshots = inject(BondSnapshotService);
  private readonly structuredData = inject(StructuredDataService);
  private readonly translate = inject(TranslateService);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly snap = signal<BondSnapshot>(this.snapshots.snapshot());
  protected readonly faq = signal<readonly { q: string; a: string }[]>([]);
  protected readonly amountUah = signal(50000);
  protected readonly termMonths = signal(12);
  /** Both sides default to the same rate (snapshot median or 16) so the delta isolates taxes. */
  protected readonly ovdpRate = signal(medianYield(this.snapshots.snapshot()));
  protected readonly depositRate = signal(medianYield(this.snapshots.snapshot()));

  protected readonly ovdpNet = computed(() =>
    ovdpNetKop(grossIncomeKop(toMinorUnits(this.amountUah()), this.ovdpRate(), this.termMonths())),
  );
  protected readonly depositNet = computed(() =>
    depositNetKop(grossIncomeKop(toMinorUnits(this.amountUah()), this.depositRate(), this.termMonths())),
  );
  protected readonly delta = computed(() => this.ovdpNet() - this.depositNet());

  ngOnInit(): void {
    this.loadFaqAndJsonLd();
    this.translate.onLangChange.subscribe(() => this.loadFaqAndJsonLd());
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

  protected num(ev: Event, fallback: number): number {
    const v = Number((ev.target as HTMLInputElement).value);
    return Number.isFinite(v) && v >= 0 ? v : fallback;
  }

  protected money(kop: number): string {
    return formatMinorUnits(kop, 'UAH');
  }

  private loadFaqAndJsonLd(): void {
    this.translate.get(['ovdp.vs.faq', 'seo.ovdpVsDeposit.title', 'seo.ovdpVsDeposit.desc']).subscribe((t) => {
      const faq: { q: string; a: string }[] = Array.isArray(t['ovdp.vs.faq']) ? t['ovdp.vs.faq'] : [];
      this.faq.set(faq);
      this.structuredData.setPageBlocks([
        {
          '@context': 'https://schema.org',
          '@type': 'WebApplication',
          name: t['seo.ovdpVsDeposit.title'],
          description: t['seo.ovdpVsDeposit.desc'],
          url: 'https://investguideua.com/ovdp/vs-deposit',
          applicationCategory: 'FinanceApplication',
          operatingSystem: 'Web',
          inLanguage: 'uk',
          isAccessibleForFree: true,
          publisher: { '@id': this.structuredData.organizationId },
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

/** Median sell yield of the snapshot, rounded to 0.1; 16 when the snapshot is empty. */
function medianYield(snapshot: BondSnapshot): number {
  const yields = snapshot.bonds.map((b) => b.sellYield).sort((a, b) => a - b);
  if (!yields.length) return 16;
  const mid = Math.floor(yields.length / 2);
  const median = yields.length % 2 ? yields[mid] : (yields[mid - 1] + yields[mid]) / 2;
  return Math.round(median * 10) / 10;
}
