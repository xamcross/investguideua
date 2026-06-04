import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Shared empty-state presenter (feature 007-ui-ux-improvements, FR-013).
 *
 * Wraps the existing global `.ig-empty` pattern so every data-driven screen renders "no data" the
 * same way (decorative mark + heading + message + optional call-to-action). Purely presentational:
 * all copy is supplied by the caller (already translated) so the component hardcodes no user text
 * (FR-023). Project CTA controls into the default slot - they inherit the global 44px button sizing.
 */
@Component({
  selector: 'ig-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ig-empty">
      <div class="ig-empty__mark" aria-hidden="true"></div>
      @if (code) {
        <span class="ig-empty__code">{{ code }}</span>
      }
      @if (heading) {
        <h2>{{ heading }}</h2>
      }
      @if (message) {
        <p>{{ message }}</p>
      }
      <div class="ig-empty__actions">
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styles: [],
})
export class EmptyStateComponent {
  /** Optional small mono eyebrow above the heading (e.g. a status code). */
  @Input() code?: string;
  /** Optional heading. */
  @Input() heading?: string;
  /** Optional supporting message. */
  @Input() message?: string;
}
