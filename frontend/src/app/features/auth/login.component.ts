import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { parseApiError } from '../../core/api/api-error.util';

/**
 * Login page (ticket FE-AUTH2, §4.1.4). On success the session is established via AuthService
 * (access token in memory, refresh via HttpOnly cookie) and the user lands on /search. On 401 a
 * single generic "invalid email or password" is shown (no user enumeration).
 */
@Component({
  selector: 'ig-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <section class="ig-card ig-auth">
      <h1>{{ 'login.title' | translate }}</h1>

      <form class="ig-form" [formGroup]="form" (ngSubmit)="submit()">
        <div class="ig-field">
          <label for="email">{{ 'login.email' | translate }}</label>
          <input id="email" type="email" formControlName="email" autocomplete="email" />
        </div>

        <div class="ig-field">
          <label for="password">{{ 'login.password' | translate }}</label>
          <input id="password" type="password" formControlName="password" autocomplete="current-password" />
        </div>

        @if (serverError()) {
          <div class="ig-alert ig-alert--error">{{ serverError() }}</div>
        }

        <button type="submit" class="ig-btn" [disabled]="submitting()">
          {{ (submitting() ? 'login.submitting' : 'login.submit') | translate }}
        </button>
      </form>

      <p class="ig-hint">{{ 'login.newHere' | translate }} <a routerLink="/register">{{ 'login.createAccount' | translate }}</a></p>
    </section>
  `,
  styles: [`.ig-auth { max-width: 480px; margin: 0 auto; } .ig-auth h1 { margin-top: 0; }`],
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  readonly submitting = signal(false);
  readonly serverError = signal<string | null>(null);

  submit(): void {
    this.serverError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigate(['/search']);
      },
      error: (err) => {
        this.submitting.set(false);
        const parsed = parseApiError(err);
        // Generic message regardless of cause (no user enumeration).
        this.serverError.set(
          parsed.code === 'UNAUTHORIZED' || parsed.code === 'VALIDATION_ERROR'
            ? this.translate.instant('login.invalidCredentials')
            : parsed.message,
        );
      },
    });
  }
}
