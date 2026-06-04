import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Shared error-state presenter (feature 007-ui-ux-improvements, FR-015).
 *
 * One consistent, recoverable error presentation across all data-driven screens. `role="alert"`
 * ensures the message is announced by assistive technology (this is the role the account screen
 * was previously missing). An optional retry control is shown only when a caller binds `(retry)`;
 * it inherits the global 44px button sizing. Project extra inline links/content into the default
 * slot. Caller supplies the translated message and optional retry label (FR-023).
 */
@Component({
  selector: 'ig-error-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ig-alert ig-alert--error" role="alert">
      <div class="ig-error-state__body">
        <span>{{ message }}</span>
        <ng-content></ng-content>
        @if (retry.observed && retryLabel) {
          <button type="button" class="ig-btn ig-btn--ghost" (click)="retry.emit()">
            {{ retryLabel }}
          </button>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .ig-error-state__body { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
    `,
  ],
})
export class ErrorStateComponent {
  /** Translated error message. */
  @Input() message = '';
  /** Translated retry label; the retry button renders only when this is set and `(retry)` is bound. */
  @Input() retryLabel?: string;
  /** Emitted when the user activates the retry control. */
  @Output() retry = new EventEmitter<void>();
}
