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
    <section class="ig-card">
      <h1>{{ 'notFound.title' | translate }}</h1>
      <p class="ig-muted">{{ 'notFound.body' | translate }}</p>
      <p><a routerLink="/">{{ 'common.backToHome' | translate }}</a></p>
    </section>
  `,
})
export class NotFoundComponent {}
