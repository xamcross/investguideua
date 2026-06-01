import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { globalErrorInterceptor } from './core/errors/global-error.interceptor';

/**
 * Root application providers (ticket FE-CORE1). Standalone bootstrap - no NgModules.
 * `withComponentInputBinding` lets routed components receive route/query params as @Input
 * (used by the Verify page to read `?token=`).
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
  ],
};
