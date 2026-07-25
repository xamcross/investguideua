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

function toSnapshot(rows: readonly BondPriceRow[]): BondSnapshot {
  const bonds: BondSnapshotEntry[] = rows
    .filter((r) => Number.isInteger(r.sellPriceMinor) && Number.isInteger(r.buyPriceMinor))
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
