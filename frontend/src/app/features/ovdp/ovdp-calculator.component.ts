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
 * /ovdp/calculator (feature 015). Prerenders a meaningful default result (25 000 грн, 12 міс, 16%)
 * so the page carries real content for crawlers; issue rates come from the build-time snapshot and
 * refresh browser-side. The custom-rate mode keeps the calculator useful with an empty snapshot.
 * All arithmetic is integer kopiykas (ovdp-math).
 */
@Component({
  selector: 'ig-ovdp-calculator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule, DatePipe],
  template: `
    <section class="ig-ovdp ig-calc" aria-labelledby="ig-calc-title">
      <p class="ig-kicker">{{ 'ovdp.calc.kicker' | translate }}</p>
      <h1 id="ig-calc-title">{{ 'ovdp.calc.title' | translate }}</h1>
      <p class="ig-lead">{{ 'ovdp.calc.intro' | translate }}</p>

      @if (snap().bonds.length) {
        <p class="ig-ovdp__asof" data-ovdp-asof>
          {{ 'ovdp.asOf' | translate }} {{ snap().fetchedAt | date: 'dd.MM.yyyy HH:mm' }}
        </p>
      } @else {
        <p class="ig-ovdp__empty" data-ovdp-empty>{{ 'ovdp.empty' | translate }}</p>
      }

      <div class="ig-calc__form">
        <label>
          <span>{{ 'ovdp.calc.amount' | translate }}</span>
          <input type="number" min="0" step="1000" [value]="amountUah()"
                 (input)="amountUah.set(num($event, 25000))" />
        </label>
        <label>
          <span>{{ 'ovdp.calc.term' | translate }}</span>
          <input type="number" min="1" max="60" step="1" [value]="termMonths()"
                 (input)="termMonths.set(num($event, 12))" />
        </label>
        <label>
          <span>{{ 'ovdp.calc.issue' | translate }}</span>
          <select (change)="selectIssue($event)">
            <option value="">{{ 'ovdp.calc.customRate' | translate }}</option>
            @for (b of snap().bonds; track b.isin) {
              <option [value]="b.isin" [selected]="selectedIsin() === b.isin">
                {{ b.isin }} - {{ b.maturity | date: 'dd.MM.yyyy' }} - {{ b.sellYield }}%
              </option>
            }
          </select>
        </label>
        @if (!selectedIsin()) {
          <label>
            <span>{{ 'ovdp.calc.ratePct' | translate }}</span>
            <input type="number" min="0" max="99" step="0.1" [value]="customRate()"
                   (input)="customRate.set(num($event, 16))" />
          </label>
        }
      </div>

      <div class="ig-calc__result">
        <h2>{{ 'ovdp.calc.resultTitle' | translate }}</h2>
        <dl>
          <div><dt>{{ 'ovdp.calc.grossLabel' | translate }}</dt><dd><b>{{ money(ovdpNet()) }}</b></dd></div>
          <div><dt>{{ 'ovdp.calc.depositLabel' | translate }}</dt><dd>{{ money(depositNet()) }}</dd></div>
          <div><dt>{{ 'ovdp.calc.totalLabel' | translate }}</dt><dd>{{ money(total()) }}</dd></div>
        </dl>
      </div>

      <h2>{{ 'ovdp.calc.h2How' | translate }}</h2>
      <p>{{ 'ovdp.calc.howBody' | translate }}</p>

      <div class="ig-ovdp__links">
        <a routerLink="/ovdp/rates" class="ig-btn ig-btn--ghost">{{ 'ovdp.calc.linkRates' | translate }}</a>
        <a routerLink="/ovdp/vs-deposit" class="ig-btn ig-btn--ghost">{{ 'ovdp.calc.linkVs' | translate }}</a>
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
      .ig-calc__form { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; margin: 1.25rem 0; }
      .ig-calc__form label { display: flex; flex-direction: column; gap: .35rem; font-size: .85rem; color: var(--muted); }
      .ig-calc__form input, .ig-calc__form select { padding: .55rem .6rem; border: 1px solid var(--surface-2);
        border-radius: var(--radius-sm); font: inherit; }
      .ig-calc__result { border: 1px solid var(--surface-2); border-left: 4px solid var(--blue-600);
        border-radius: var(--radius-sm); padding: 1rem 1.25rem; }
      .ig-calc__result h2 { margin: 0 0 .75rem; font-size: 1.05rem; }
      .ig-calc__result dl { margin: 0; }
      .ig-calc__result dl div { display: flex; justify-content: space-between; gap: 1rem; padding: .3rem 0; }
      .ig-calc__result dt { color: var(--muted); }
    `,
  ],
})
export class OvdpCalculatorComponent implements OnInit, OnDestroy {
  private readonly snapshots = inject(BondSnapshotService);
  private readonly structuredData = inject(StructuredDataService);
  private readonly translate = inject(TranslateService);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly snap = signal<BondSnapshot>(this.snapshots.snapshot());
  protected readonly faq = signal<readonly { q: string; a: string }[]>([]);
  protected readonly amountUah = signal(25000);
  protected readonly termMonths = signal(12);
  /** Empty string = custom-rate mode (the prerendered default, independent of snapshot content). */
  protected readonly selectedIsin = signal('');
  protected readonly customRate = signal(16);

  protected readonly rate = computed(() => {
    const isin = this.selectedIsin();
    const bond = isin ? this.snap().bonds.find((b) => b.isin === isin) : undefined;
    return bond ? bond.sellYield : this.customRate();
  });
  protected readonly gross = computed(() =>
    grossIncomeKop(toMinorUnits(this.amountUah()), this.rate(), this.termMonths()),
  );
  protected readonly ovdpNet = computed(() => ovdpNetKop(this.gross()));
  protected readonly depositNet = computed(() => depositNetKop(this.gross()));
  protected readonly total = computed(() => toMinorUnits(this.amountUah()) + this.ovdpNet());

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

  protected selectIssue(ev: Event): void {
    this.selectedIsin.set((ev.target as HTMLSelectElement).value);
  }

  protected num(ev: Event, fallback: number): number {
    const v = Number((ev.target as HTMLInputElement).value);
    return Number.isFinite(v) && v >= 0 ? v : fallback;
  }

  protected money(kop: number): string {
    return formatMinorUnits(kop, 'UAH');
  }

  private loadFaqAndJsonLd(): void {
    this.translate.get(['ovdp.calc.faq', 'seo.ovdpCalculator.title', 'seo.ovdpCalculator.desc']).subscribe((t) => {
      const faq: { q: string; a: string }[] = Array.isArray(t['ovdp.calc.faq']) ? t['ovdp.calc.faq'] : [];
      this.faq.set(faq);
      this.structuredData.setPageBlocks([
        {
          '@context': 'https://schema.org',
          '@type': 'WebApplication',
          name: t['seo.ovdpCalculator.title'],
          description: t['seo.ovdpCalculator.desc'],
          url: 'https://investguideua.com/ovdp/calculator',
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
