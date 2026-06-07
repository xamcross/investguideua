import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Cloudflare Web Analytics loader (feature 010-seo-aeo-optimization, US3 / FR-009).
 *
 * Appends the cookieless, PII-free Cloudflare beacon to the document head ONCE at app bootstrap,
 * using the same head-append mechanism as {@link StructuredDataService} so the snippet serializes
 * into the prerendered static HTML of every public page (audit A25). No cookies => no consent
 * banner. The beacon is intentionally site-wide: private routes are served the prerendered home
 * shell via the Cloudflare SPA fallback, so true "absent on private" is not achievable by a runtime
 * guard and carries no value for a cookieless beacon (see plan research R2).
 *
 * No-ops when the token is empty (local dev / unconfigured builds) or if already injected.
 */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly document = inject(DOCUMENT);
  private readonly token = environment.cloudflareAnalyticsToken;

  /** Inject the Cloudflare Web Analytics beacon once. Safe to call on every bootstrap. */
  init(): void {
    if (!this.token) {
      return;
    }
    if (this.document.head.querySelector('script[data-cf-beacon]')) {
      return;
    }
    const script = this.document.createElement('script');
    script.setAttribute('defer', '');
    script.setAttribute('src', 'https://static.cloudflareinsights.com/beacon.min.js');
    script.setAttribute('data-cf-beacon', JSON.stringify({ token: this.token }));
    this.document.head.appendChild(script);
  }
}
