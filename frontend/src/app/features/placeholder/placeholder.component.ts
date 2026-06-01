import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Lightweight stand-in for §12 pages whose feature tickets are not yet implemented (Search,
 * History, Tokens, Account, Providers). Lets all FE-CORE1 routes resolve. The `heading` comes from
 * the route `data` via component input binding.
 */
@Component({
  selector: 'ig-placeholder',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="ig-card">
      <h1>{{ heading || 'Coming soon' }}</h1>
      <p class="ig-muted">This area is part of a later delivery milestone and is not built yet.</p>
      <p><a routerLink="/">Back to home</a></p>
    </section>
  `,
})
export class PlaceholderComponent {
  @Input() heading?: string;
}
