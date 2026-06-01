import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** 404 fallback for unknown routes (FE-CORE1 DoD: unknown route -> a 404/redirect). */
@Component({
  selector: 'ig-not-found',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="ig-card">
      <h1>404 - Page not found</h1>
      <p class="ig-muted">The page you are looking for does not exist.</p>
      <p><a routerLink="/">Back to home</a></p>
    </section>
  `,
})
export class NotFoundComponent {}
