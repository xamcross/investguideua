import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { skipGlobalError } from '../../core/errors/global-error.interceptor';
import { BOND_SNAPSHOT, BondSnapshot, BondSnapshotEntry } from './bond-snapshot.data';

/** Raw row shape of GET /bond-prices/latest (mirrors the backend BondPriceResponse). */
interface BondPriceRow {
  isin: string;
  military: boolean;
  currency: string;
  maturity: string;
  quotationDate: string;
  sellPriceMinor: number;
  buyPriceMinor: number;
  sellYield: number;
  buyYield: number;
  fetchedAt: string;
}

/**
 * Bond quote access for the /ovdp data pages (feature 015).
 *
 * {@link snapshot} is the build-time bundle (generated bond-snapshot.data.ts) - available during
 * prerendering with zero network I/O, so crawlers always see data. {@link latest} is the public
 * API read used ONLY browser-side to refresh the view after hydration; callers guard it with
 * isPlatformBrowser and treat failures as "keep the snapshot" (hence skipGlobalError - a failed
 * refresh must not toast).
 */
@Injectable({ providedIn: 'root' })
export class BondSnapshotService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  snapshot(): BondSnapshot {
    return BOND_SNAPSHOT;
  }

  latest(): Observable<BondSnapshot> {
    return this.http
      .get<BondPriceRow[]>(`${this.base}/bond-prices/latest`, { context: skipGlobalError() })
      .pipe(map((rows) => toSnapshot(rows)));
  }
}

const CURRENCIES = new Set(['UAH', 'USD', 'EUR']);

/** Mirrors the build-time sanitize() in tools/seo/fetch-bond-snapshot.mjs - keep the two aligned. */
function toSnapshot(rows: readonly BondPriceRow[]): BondSnapshot {
  // Matured quotes must never render (defense-in-depth: the backend filters too, but an old
  // backend or cached payload must not surface zombies). ISO yyyy-MM-dd compares lexically.
  const today = new Date().toISOString().slice(0, 10);
  const bonds: BondSnapshotEntry[] = rows
    .filter(
      (r) =>
        Number.isInteger(r.sellPriceMinor) &&
        Number.isInteger(r.buyPriceMinor) &&
        Number.isFinite(r.sellYield) &&
        Number.isFinite(r.buyYield) &&
        CURRENCIES.has(r.currency) &&
        /^\d{4}-\d{2}-\d{2}$/.test(r.maturity ?? '') &&
        r.maturity >= today,
    )
    .map((r) => ({
      isin: r.isin,
      military: r.military,
      currency: r.currency,
      maturity: r.maturity,
      sellPriceMinor: r.sellPriceMinor,
      buyPriceMinor: r.buyPriceMinor,
      sellYield: r.sellYield,
      buyYield: r.buyYield,
    }))
    .sort((a, b) => a.maturity.localeCompare(b.maturity) || a.isin.localeCompare(b.isin));
  const fetchedAt = rows.map((r) => r.fetchedAt ?? '').filter(Boolean).sort().pop() ?? null;
  return { fetchedAt, bonds };
}
