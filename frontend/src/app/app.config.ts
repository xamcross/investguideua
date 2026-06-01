import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, TitleStrategy } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { globalErrorInterceptor } from './core/errors/global-error.interceptor';
import { DEFAULT_LANG } from './core/i18n/language.service';
import { TranslatedTitleStrategy } from './core/i18n/translated-title.strategy';

/**
 * Root application providers (ticket FE-CORE1). Standalone bootstrap - no NgModules.
 * `withComponentInputBinding` lets routed components receive route/query params as @Input
 * (used by the Verify page to read `?token=`).
 *
 * i18n (ngx-translate): JSON dictionaries are loaded at runtime from `/i18n/<lang>.json` so the user
 * can switch language live with no rebuild. Ukrainian is the fallback/default; the resolved startup
 * language is applied by LanguageService.init in the shell. TranslatedTitleStrategy localizes the
 * document/tab title from a translation key on each route.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    // Order matters: globalErrorInterceptor is OUTERMOST so it only sees errors the auth
    // interceptor did not recover from (e.g. a refreshed 401 never reaches it). FE-CORE4 / FE-CORE2.
    provideHttpClient(
      withFetch(),
      withInterceptors([globalErrorInterceptor, authInterceptor]),
    ),
    provideTranslateService({
      fallbackLang: DEFAULT_LANG,
      lang: DEFAULT_LANG,
      loader: provideTranslateHttpLoader({ prefix: 'i18n/', suffix: '.json' }),
    }),
    { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
  ],
};
