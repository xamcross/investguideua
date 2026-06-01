import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DEFAULT_LANG } from './language.service';

/**
 * Locale-aware count + noun formatter, e.g. `5 | igPlural:'token'` -> "5 токенів" / "5 tokens".
 *
 * English has the trivial one/other split; Ukrainian needs the Slavic three-form rule
 * (one / few / many), which a flat key lookup cannot express. The word forms live in the translation
 * files under `units.<noun>.{one,few,many}`; this pipe only selects the right form for the active
 * language. It is impure so it re-evaluates when the language changes at runtime.
 */
@Pipe({ name: 'igPlural', standalone: true, pure: false })
export class PluralPipe implements PipeTransform {
  private readonly translate = inject(TranslateService);

  transform(count: number | null | undefined, noun: string): string {
    const n = Math.trunc(Math.abs(Number(count ?? 0)));
    // currentLang can be momentarily undefined before the first `use()` resolves; fall back to the
    // product default so Ukrainian counts never briefly pick the English form on a cold load.
    const lang = this.translate.currentLang || DEFAULT_LANG;
    const forms = this.translate.instant(`units.${noun}`) as Record<string, string>;
    const form = this.pick(n, lang);
    const word = forms?.[form] ?? forms?.['many'] ?? noun;
    return `${n} ${word}`;
  }

  /** Returns the form key 'one' | 'few' | 'many' for the count in the given language. */
  private pick(n: number, lang: string): 'one' | 'few' | 'many' {
    if (lang === 'uk') {
      const mod10 = n % 10;
      const mod100 = n % 100;
      if (mod100 >= 11 && mod100 <= 14) {
        return 'many';
      }
      if (mod10 === 1) {
        return 'one';
      }
      if (mod10 >= 2 && mod10 <= 4) {
        return 'few';
      }
      return 'many';
    }
    // English (and fallback): one vs other; 'few' carries the plural form.
    return n === 1 ? 'one' : 'few';
  }
}
