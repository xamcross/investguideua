import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { parseApiError } from '../../core/api/api-error.util';

/**
 * Registration page (ticket FE-AUTH1, §4.1, §5.2).
 *
 * Client validation mirrors the server policy (email format; password >= 8 chars with >= 1 letter
 * and >= 1 digit) but the server remains authoritative - a slipped-through VALIDATION_ERROR is
 * still surfaced. On 409 EMAIL_TAKEN we show an inline message; on success we show a
 * "check your email" state and make explicit that the 5 free tokens arrive only after verification.
 */
@Component({
  selector: 'ig-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <section class="ig-card ig-auth">
      @if (registeredEmail()) {
        <h1>{{ 'register.checkEmailTitle' | translate }}</h1>
        <div class="ig-alert ig-alert--success">
          {{ 'register.sentLink' | translate: { email: registeredEmail() } }}
        </div>
        <p>{{ 'register.verifyToActivate' | translate }}</p>
        <p class="ig-hint">{{ 'register.didntGet' | translate }}</p>
        <p><a routerLink="/login">{{ 'register.goToSignIn' | translate }}</a></p>
      } @else {
        <h1>{{ 'register.createTitle' | translate }}</h1>
        <p class="ig-muted">{{ 'register.subtitle' | translate }}</p>

        <form class="ig-form" [formGroup]="form" (ngSubmit)="submit()">
          <div class="ig-field">
            <label for="email">{{ 'register.email' | translate }}</label>
            <input id="email" type="email" formControlName="email" autocomplete="email" />
            @if (showError('email')) {
              <span class="ig-error">{{ 'register.emailError' | translate }}</span>
            }
          </div>

          <div class="ig-field">
            <label for="password">{{ 'register.password' | translate }}</label>
            <input id="password" type="password" formControlName="password" autocomplete="new-password" />
            @if (showError('password')) {
              <span class="ig-error">{{ 'register.passwordError' | translate }}</span>
            }
          </div>

          @if (serverError()) {
            <div class="ig-alert ig-alert--error">{{ serverError() }}</div>
          }

          <button type="submit" class="ig-btn" [disabled]="submitting()">
            {{ (submitting() ? 'register.submitting' : 'register.submit') | translate }}
          </button>
        </form>

        <p class="ig-hint">{{ 'register.alreadyHave' | translate }} <a routerLink="/login">{{ 'register.signIn' | translate }}</a></p>
      }
    </section>
  `,
  styles: [`.ig-auth { max-width: 480px; margin: 0 auto; } .ig-auth h1 { margin-top: 0; }`],
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly translate = inject(TranslateService);

  // Mirror server policy: >= 1 letter, >= 1 digit, >= 8 chars.
  private static readonly PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/;

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.pattern(RegisterComponent.PASSWORD_PATTERN)]],
  });

  readonly submitting = signal(false);
  readonly serverError = signal<string | null>(null);
  readonly registeredEmail = signal<string | null>(null);

  showError(control: 'email' | 'password'): boolean {
    const c = this.form.controls[control];
    return c.invalid && (c.touched || c.dirty);
  }

  submit(): void {
    this.serverError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.register(email, password).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.registeredEmail.set(res.email);
      },
      error: (err) => {
        this.submitting.set(false);
        const parsed = parseApiError(err);
        if (parsed.code === 'EMAIL_TAKEN') {
          this.serverError.set(this.translate.instant('register.emailTaken'));
        } else if (parsed.code === 'VALIDATION_ERROR') {
          this.serverError.set(this.translate.instant('register.validationError'));
        } else {
          this.serverError.set(parsed.message);
        }
      },
    });
  }
}
