import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { NotificationService } from './notification.service';

/**
 * Renders the global notification (toast) stack (ticket FE-CORE4). Mounted once in the app shell.
 *
 * Each toast shows the mapped title/message, an optional contextual action (e.g. "Buy tokens"), and —
 * for support — a copyable `requestId` matching the `X-Request-Id` the backend logged. Dismissible.
 */
@Component({
  selector: 'ig-notification-host',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule],
  template: `
    <div class="ig-toasts" aria-live="polite" aria-atomic="true">
      @for (toast of notifications.toasts(); track toast.id) {
        <div class="ig-toast" [class.ig-toast--error]="toast.severity === 'error'"
             [class.ig-toast--warning]="toast.severity === 'warning'"
             [class.ig-toast--info]="toast.severity === 'info'" role="status">
          <div class="ig-toast__body">
            <strong class="ig-toast__title">{{ toast.title | translate }}</strong>
            <span class="ig-toast__msg">{{ toast.messageText || (toast.message | translate) }}</span>
            <div class="ig-toast__row">
              @if (toast.action) {
                <a class="ig-toast__action" [routerLink]="toast.action.route"
                   (click)="notifications.dismiss(toast.id)">{{ toast.action.label | translate }}</a>
              }
              @if (toast.requestId) {
                <button type="button" class="ig-toast__ref" (click)="copyRef(toast.requestId!)"
                        [title]="'toast.copyRef' | translate: { id: toast.requestId }">
                  {{ 'toast.ref' | translate: { id: shortRef(toast.requestId) } }}{{ copiedId() === toast.requestId ? ' ✓' : '' }}
                </button>
              }
            </div>
          </div>
          <button type="button" class="ig-toast__close" [attr.aria-label]="'toast.dismiss' | translate"
                  (click)="notifications.dismiss(toast.id)">&times;</button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .ig-toasts {
        position: fixed; top: 1rem; right: 1rem; z-index: 1000;
        display: flex; flex-direction: column; gap: 0.6rem;
        max-width: min(360px, calc(100vw - 2rem));
      }
      .ig-toast {
        display: flex; align-items: flex-start; gap: 0.5rem;
        background: var(--surface); border: 1px solid var(--line);
        border-left-width: 4px; border-radius: var(--radius-sm); padding: 0.75rem 0.9rem;
        box-shadow: var(--shadow-md);
      }
      .ig-toast--error { border-left-color: var(--danger-fg); }
      .ig-toast--warning { border-left-color: var(--gold-600); }
      .ig-toast--info { border-left-color: var(--blue-600); }
      .ig-toast__body { display: flex; flex-direction: column; gap: 0.2rem; flex: 1; }
      .ig-toast__title { font-size: 0.92rem; color: var(--ink); }
      .ig-toast__msg { font-size: 0.85rem; color: var(--muted); }
      .ig-toast__row { display: flex; align-items: center; gap: 0.75rem; margin-top: 0.25rem; flex-wrap: wrap; }
      .ig-toast__action { font-size: 0.82rem; font-weight: 700; color: var(--blue-600); text-decoration: none; }
      .ig-toast__ref {
        font-size: 0.72rem; color: var(--muted); background: none; border: none;
        min-height: var(--touch-min); display: inline-flex; align-items: center;
        padding: 0 .25rem; cursor: pointer; font-family: var(--font-mono);
      }
      .ig-toast__ref:hover { color: var(--ink); }
      .ig-toast__close {
        background: none; border: none; font-size: 1.3rem; line-height: 1; cursor: pointer;
        color: var(--muted); flex: none;
        min-width: var(--touch-min); min-height: var(--touch-min);
        display: inline-flex; align-items: center; justify-content: center;
        margin: -0.35rem -0.4rem -0.35rem 0;   /* keep visual footprint tight while hit area >= 44px */
      }
    `,
  ],
})
export class NotificationHostComponent {
  protected readonly notifications = inject(NotificationService);
  protected readonly copiedId = signal<string | null>(null);

  shortRef(requestId: string): string {
    return requestId.length > 8 ? requestId.slice(0, 8) : requestId;
  }

  copyRef(requestId: string): void {
    const done = () => {
      this.copiedId.set(requestId);
      setTimeout(() => this.copiedId.set(null), 1500);
    };
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(requestId).then(done).catch(done);
    } else {
      done();
    }
  }
}
