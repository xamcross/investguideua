import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { SearchCurrency } from '../investment/investment.models';

/**
 * Localized currency *label* (display word only). UAH -> 'грн' (uk) / 'UAH' (en); USD -> 'USD'.
 * NEVER touches amounts or minor-unit math. Impure so it re-renders on runtime language switch.
 */
@Pipe({ name: 'igCurrency', standalone: true, pure: false })
export class CurrencyLabelPipe implements PipeTransform {
  private readonly translate = inject(TranslateService);
  transform(code: SearchCurrency | string | null | undefined): string {
    if (!code) { return ''; }
    return this.translate.instant('currency.' + code) || String(code);
  }
}
