import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

/**
 * SPA route-focus manager (feature 007-ui-ux-improvements, FR-001 / C-A11Y-2).
 *
 * On a single-page app, a client-side navigation does not reset focus the way a full page load
 * does - it stays on the activated link, so keyboard and screen-reader users lose their place and
 * are not told the page changed. After each {@link NavigationEnd} this service moves focus to the
 * main landmark (`#main-content`), which is the same target the skip link points at. The landmark
 * carries `tabindex="-1"` so it is programmatically focusable without entering the tab order.
 *
 * SSR-safe: all DOM access is guarded by `isPlatformBrowser`, so it is a no-op during server
 * render / prerender.
 */
@Injectable({ providedIn: 'root' })
export class RouteFocusService {
  private readonly router = inject(Router);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private started = false;

  /** Begin listening for navigations. Called once from the app shell after the first paint. */
  init(): void {
    if (!this.isBrowser || this.started) {
      return;
    }
    this.started = true;
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(() => this.focusMain());
  }

  /** Move focus to the main landmark (or its first heading) if present. */
  private focusMain(): void {
    const main = document.getElementById('main-content');
    if (!main) {
      return;
    }
    const target = (main.querySelector('h1') as HTMLElement | null) ?? main;
    // Headings are not focusable by default; make the chosen target focusable for this move.
    if (!target.hasAttribute('tabindex')) {
      target.setAttribute('tabindex', '-1');
    }
    target.focus({ preventScroll: false });
  }
}
