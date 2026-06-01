import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

/** Supported UI languages. Ukrainian is the product default (project: UA-first). */
export type AppLang = 'uk' | 'en';

export const SUPPORTED_LANGS: readonly AppLang[] = ['uk', 'en'] as const;
export const DEFAULT_LANG: AppLang = 'uk';

/** localStorage key for the user's explicit language choice. */
const STORAGE_KEY = 'ig.lang';

/**
 * Central runtime language control (i18n).
 *
 * Per the UI/UX decision we ALWAYS default to Ukrainian and never auto-detect from
 * `navigator.language`: this is a deliberately UA-first product and silent locale detection produces
 * surprising first loads (many UA users run en-US browsers). Resolution order is simply
 * `localStorage('ig.lang')` -> `'uk'`. The choice is persisted only on an explicit user action.
 *
 * On every change we also keep `<html lang>` in sync (a11y / SEO) and expose a short `announcement`
 * signal that the shell renders in an `aria-live` region so screen-reader users get feedback. The
 * announcement fires only on an explicit user switch, not on the initial startup application.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);

  /** Reactive current language for templates (e.g. the header toggle). */
  readonly current = signal<AppLang>(DEFAULT_LANG);
  /** Polite screen-reader announcement text, updated on each explicit switch. */
  readonly announcement = signal<string>('');

  /** Wire ngx-translate and apply the resolved startup language. Call once at app start. */
  init(): void {
    this.translate.addLangs([...SUPPORTED_LANGS]);
    this.translate.setDefaultLang(DEFAULT_LANG);
    this.apply(this.resolveInitial(), false);
  }

  /** Switch to an explicit language and persist the choice. */
  use(lang: AppLang): void {
    if (!SUPPORTED_LANGS.includes(lang) || lang === this.current()) {
      return;
    }
    this.apply(lang, true);
  }

  /** Two-state toggle for the header switcher. */
  toggle(): void {
    this.use(this.current() === 'uk' ? 'en' : 'uk');
  }

  private apply(lang: AppLang, announce: boolean): void {
    this.translate.use(lang);
    this.current.set(lang);
    this.document.documentElement.lang = lang;
    if (announce) {
      this.persist(lang);
      // Announce in the language just selected (key is resolved after `use`). Startup does not
      // announce (announce=false) so screen readers are not interrupted on initial page load.
      this.translate.get('lang.announced').subscribe((msg) => this.announcement.set(String(msg)));
    }
  }

  private resolveInitial(): AppLang {
    return this.read() ?? DEFAULT_LANG;
  }

  private read(): AppLang | null {
    try {
      const v = this.window()?.localStorage?.getItem(STORAGE_KEY);
      return v === 'uk' || v === 'en' ? v : null;
    } catch {
      return null; // private mode / storage disabled - fall back to default.
    }
  }

  private persist(lang: AppLang): void {
    try {
      this.window()?.localStorage?.setItem(STORAGE_KEY, lang);
    } catch {
      // Non-fatal: the language still applies for this session.
    }
  }

  private window(): (Window & typeof globalThis) | null {
    return this.document.defaultView;
  }
}
