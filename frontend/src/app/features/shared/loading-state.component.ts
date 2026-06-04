import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Shared loading-state presenter (feature 007-ui-ux-improvements, FR-014).
 *
 * One consistent loading indication across all data-driven screens. `role="status"` exposes the
 * label to assistive technology; the spinner animation is suppressed under
 * `prefers-reduced-motion` (FR-004), leaving the visible label. Caller supplies the (translated)
 * label so no copy is hardcoded (FR-023).
 */
@Component({
  selector: 'ig-loading-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="ig-loading" role="status">
      <span class="ig-loading__spinner" aria-hidden="true"></span>
      <span>{{ label }}</span>
    </p>
  `,
  styles: [
    `
      .ig-loading {
        display: inline-flex; align-items: center; gap: .6rem;
        color: var(--muted); font-size: var(--text-sm); margin: 0;
      }
      .ig-loading__spinner {
        width: 1.1rem; height: 1.1rem; flex: none; border-radius: 50%;
        border: 2px solid var(--line-2); border-top-color: var(--blue-600);
        animation: ig-spin .7s linear infinite;
      }
      @keyframes ig-spin { to { transform: rotate(360deg); } }
      @media (prefers-reduced-motion: reduce) {
        .ig-loading__spinner { animation: none; }
      }
    `,
  ],
})
export class LoadingStateComponent {
  /** Translated loading label (e.g. "Loading..."). */
  @Input() label = '';
}
