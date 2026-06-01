import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * Lightweight stand-in for §12 pages whose feature tickets are not yet implemented (Search,
 * History, Tokens, Account, Providers). Lets all FE-CORE1 routes resolve. The `heading` comes from
 * the route `data` via component input binding.
 */
@Component({
  selector: 'ig-placeholder',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe],
  template: `
    <section class="ig-card">
      <h1>{{ heading || ('placeholder.comingSoon' | translate) }}</h1>
      <p class="ig-muted">{{ 'placeholder.body' | translate }}</p>
      <p><a routerLink="/">{{ 'common.backToHome' | translate }}</a></p>
    </section>
  `,
})
export class PlaceholderComponent {
  @Input() heading?: string;
}
