import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

/**
 * Lightweight stand-in for §12 pages whose feature tickets are not yet implemented (Search,
 * History, Tokens, Account, Providers). Lets all FE-CORE1 routes resolve. The `heading` comes from
 * the route `data` via component input binding.
 */
@Component({
  selector: 'ig-placeholder',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule],
  template: `
    <section class="ig-empty">
      <div class="ig-empty__mark" aria-hidden="true"></div>
      <span class="ig-empty__code">{{ 'placeholder.badge' | translate }}</span>
      <h1>{{ heading || ('placeholder.comingSoon' | translate) }}</h1>
      <p>{{ 'placeholder.body' | translate }}</p>
      <div class="ig-empty__actions">
        <a routerLink="/" class="ig-btn ig-btn--primary">{{ 'common.backToHome' | translate }}</a>
      </div>
    </section>
  `,
})
export class PlaceholderComponent {
  @Input() heading?: string;
}
