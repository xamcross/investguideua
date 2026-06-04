import { ApplicationConfig, mergeApplicationConfig } from '@angular/core';
import { provideServerRendering } from '@angular/platform-server';
import { TranslateLoader } from '@ngx-translate/core';
import { appConfig } from './app.config';
import { ServerTranslateLoader } from './core/seo/server-translate.loader';

/**
 * Server/prerender application config (feature 006-seo-optimization).
 *
 * Used ONLY at build time to prerender public routes to static HTML; no server is deployed in
 * production (Constitution Principle I). It layers two things on top of the browser `appConfig`:
 *   1. `provideServerRendering()` - the platform-server renderer.
 *   2. A filesystem `TranslateLoader` override so translations resolve from disk during prerender
 *      instead of an (origin-less) HTTP fetch.
 *
 * `mergeApplicationConfig` appends these providers; the later TranslateLoader provider wins over the
 * HTTP loader declared in `appConfig` on the server platform.
 */
const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(),
    { provide: TranslateLoader, useClass: ServerTranslateLoader },
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
