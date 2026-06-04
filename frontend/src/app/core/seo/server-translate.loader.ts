import { TranslateLoader } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Server/prerender-time translation loader (feature 006-seo-optimization, R2).
 *
 * During build-time prerendering the app runs on `@angular/platform-server`, where the browser
 * `TranslateHttpLoader` cannot fetch `/i18n/<lang>.json` (a relative URL has no origin on the
 * server and the request would fail or hang). This loader instead reads the SAME dictionary files
 * straight from disk (`public/i18n/<lang>.json`), so prerendered HTML contains fully translated
 * text. The browser keeps using the HTTP loader after hydration - the dictionaries are identical,
 * so the rendered DOM matches and Angular hydration does not error.
 *
 * NOTE: This module is only provided on the server platform (see app.config.server.ts); it imports
 * Node built-ins and must never be bundled into the browser build.
 */
export class ServerTranslateLoader implements TranslateLoader {
  /** Absolute path to the directory holding `<lang>.json` files. Defaults to `<cwd>/public/i18n`. */
  constructor(private readonly i18nDir: string = join(process.cwd(), 'public', 'i18n')) {}

  getTranslation(lang: string): Observable<Record<string, unknown>> {
    try {
      const raw = readFileSync(join(this.i18nDir, `${lang}.json`), 'utf-8');
      return of(JSON.parse(raw) as Record<string, unknown>);
    } catch {
      // Missing/unreadable dictionary: return empty so prerender does not crash; the browser
      // loader will fetch the real file on hydration.
      return of({});
    }
  }
}
