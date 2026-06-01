import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Guest landing page (ticket FE-AUTH0, §3 Guest role, §12 Landing). Product summary, how it works,
 * and Register/Login CTAs. Makes no authenticated API calls. Authenticated users are redirected to
 * /search.
 */
@Component({
  selector: 'ig-landing',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe],
  template: `
    <section class="ig-hero ig-card">
      <h1>{{ 'landing.title' | translate }}</h1>
      <p class="ig-lead">{{ 'landing.lead' | translate }}</p>
      <div class="ig-cta">
        <a routerLink="/register" class="ig-btn">{{ 'landing.ctaStart' | translate }}</a>
        <a routerLink="/login" class="ig-btn ig-btn--ghost">{{ 'landing.ctaSignIn' | translate }}</a>
      </div>
      <p class="ig-hint">{{ 'landing.hint' | translate }}</p>
    </section>

    <section class="ig-steps">
      <div class="ig-card ig-step">
        <span class="ig-step__num">1</span>
        <h3>{{ 'landing.step1Title' | translate }}</h3>
        <p class="ig-muted">{{ 'landing.step1Body' | translate }}</p>
      </div>
      <div class="ig-card ig-step">
        <span class="ig-step__num">2</span>
        <h3>{{ 'landing.step2Title' | translate }}</h3>
        <p class="ig-muted">{{ 'landing.step2Body' | translate }}</p>
      </div>
      <div class="ig-card ig-step">
        <span class="ig-step__num">3</span>
        <h3>{{ 'landing.step3Title' | translate }}</h3>
        <p class="ig-muted">{{ 'landing.step3Body' | translate }}</p>
      </div>
    </section>

    <p class="ig-disclaimer ig-muted">{{ 'landing.disclaimer' | translate }}</p>
  `,
  styles: [
    `
      .ig-hero { text-align: center; padding: 2.5rem 1.5rem; }
      .ig-hero h1 { color: var(--ig-blue); font-size: 2rem; margin: 0 0 0.75rem; }
      .ig-lead { font-size: 1.1rem; max-width: 620px; margin: 0 auto 1.5rem; }
      .ig-cta { display: flex; gap: 0.75rem; justify-content: center; flex-wrap: wrap; }
      .ig-steps { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1rem; margin: 1.5rem 0; }
      .ig-step { position: relative; }
      .ig-step__num {
        display: inline-flex; align-items: center; justify-content: center;
        width: 28px; height: 28px; border-radius: 50%; background: var(--ig-blue); color: #fff;
        font-weight: 700; margin-bottom: 0.5rem;
      }
      .ig-step h3 { margin: 0.25rem 0; }
      .ig-disclaimer { font-size: 0.82rem; text-align: center; }
    `,
  ],
})
export class LandingComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      void this.router.navigate(['/search']);
    }
  }
}
