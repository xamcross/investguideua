import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

/** 404 fallback for unknown routes (FE-CORE1 DoD: unknown route -> a 404/redirect). */
@Component({
  selector: 'ig-not-found',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule],
  template: `
    <section class="ig-empty">
      <div class="ig-empty__mark" aria-hidden="true"></div>
      <span class="ig-empty__code">404</span>
      <h1>{{ 'notFound.title' | translate }}</h1>
      <p>{{ 'notFound.body' | translate }}</p>
      <div class="ig-empty__actions">
        <a routerLink="/" class="ig-btn ig-btn--primary">{{ 'common.backToHome' | translate }}</a>
      </div>
    </section>
  `,
})
export class NotFoundComponent {}
