import { ApplicationConfig, importProvidersFrom, provideZoneChangeDetection } from '@angular/core';
import { provideClientHydration } from '@angular/platform-browser';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling, TitleStrategy } from '@angular/router';
import { HttpBackend, HttpClient, provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { globalErrorInterceptor } from './core/errors/global-error.interceptor';
import { DEFAULT_LANG } from './core/i18n/language.service';
import { TranslatedTitleStrategy } from './core/i18n/translated-title.strategy';

/**
 * Builds the runtime translation loader. Dictionaries are fetched from `/i18n/<lang>.json`
 * (served from `public/i18n` via the `angular.json` assets entry).
 *
 * It is built on HttpBackend (the raw handler), NOT HttpClient, so translation-file requests bypass
 * the auth + global-error interceptors. That is both correct (static assets need no auth header and
 * must not raise the global error toast) and necessary: routing HttpClient through authInterceptor
 * (which injects Router) while the TitleStrategy -> TranslateService chain is constructed during
 * Router creation forms an NG0200 circular dependency. HttpBackend severs that cycle.
 *
 * ngx-translate is pinned to the 15.x line because it is compiled against Angular 16 and links
 * cleanly under Angular 17.3. The 16/17 majors are built against Angular 18/20 and their Ivy
 * partial declarations are NOT linkable by the Angular 17 compiler (they crash app bootstrap with a
 * DI "reading 'root'" error). 15.x is the correct version-matched choice for this app.
 */
export function createTranslateLoader(handler: HttpBackend): TranslateHttpLoader {
  return new TranslateHttpLoader(new HttpClient(handler), 'i18n/', '.json');
}

/**
 * Root application providers (ticket FE-CORE1). Standalone bootstrap - no NgModules.
 * `withComponentInputBinding` lets routed components receive route/query params as @Input
 * (used by the Verify page to read `?token=`).
 *
 * i18n (ngx-translate): JSON dictionaries are loaded at runtime from `/i18n/<lang>.json` so the user
 * can switch language live with no rebuild. Ukrainian is the default; the resolved startup language
 * is applied by LanguageService.init in the shell. TranslatedTitleStrategy localizes the document
 * title from a translation key on each route.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // scrollPositionRestoration 'top' resets scroll to the page top on forward navigation
    // (so e.g. the pricing-section Register CTA opens /register at the top, not mid-form) while
    // restoring the prior offset on back/forward. anchorScrolling enables in-page #fragment jumps.
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
    ),
    // Order matters: globalErrorInterceptor is OUTERMOST so it only sees errors the auth
    // interceptor did not recover from (e.g. a refreshed 401 never reaches it). FE-CORE4 / FE-CORE2.
    // Client-side hydration of the prerendered HTML (feature 006-seo-optimization). The server
    // renders public routes to static HTML at build time; on load the browser reuses that DOM
    // instead of re-rendering from scratch, preserving the prerendered (crawlable) content.
    provideClientHydration(),
    provideHttpClient(
      withFetch(),
      withInterceptors([globalErrorInterceptor, authInterceptor]),
    ),
    importProvidersFrom(
      TranslateModule.forRoot({
        defaultLanguage: DEFAULT_LANG,
        loader: {
          provide: TranslateLoader,
          useFactory: createTranslateLoader,
          deps: [HttpBackend],
        },
      }),
    ),
    { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
  ],
};
