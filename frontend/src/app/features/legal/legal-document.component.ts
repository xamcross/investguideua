import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  Input,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

/** One titled section of a legal document. `body` and `bullets` are both optional, but at least one
 * must be non-empty for the section to render anything meaningful. */
interface LegalSection {
  heading: string;
  body?: string[];
  bullets?: string[];
}

/** Shape of a `legal.terms` / `legal.privacy` object read from the i18n dictionary. */
interface LegalDocumentContent {
  title: string;
  effectiveDate: string;
  intro?: string;
  sections: LegalSection[];
}

/**
 * Public Terms & Conditions / Privacy Statement screen (feature 004). A single shared, standalone,
 * OnPush component renders both `/terms` and `/privacy`; the route selects which document via
 * `data: { doc: 'terms' | 'privacy' }` bound to `@Input() doc` (app-wide `withComponentInputBinding`).
 *
 * Content lives entirely in the i18n dictionaries under `legal.<doc>` (structured: title, effective
 * date, optional intro, and an ordered `sections` array). It is read as a whole object via
 * `TranslateService.get(...)` - NOT the `translate` pipe (which cannot render an object) and NOT
 * `instant()` (these routes are lazy-loaded and directly URL-addressable, so a cold deep-link can run
 * before the dictionary has loaded, at which point `instant()` returns the raw key). The content is
 * re-read on `onLangChange` so switching language swaps it in place. If the resolved value is not an
 * object with a non-empty `sections` array, a safe loading fallback renders instead of a raw key.
 */
@Component({
  selector: 'ig-legal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule],
  template: `
    @if (content(); as d) {
      <article class="ig-legal">
        <header class="ig-legal__head">
          <h1>{{ d.title }}</h1>
          <p class="ig-legal__eff">{{ 'legal.effectiveLabel' | translate }} {{ d.effectiveDate }}</p>
        </header>

        @if (d.intro) {
          <p class="ig-legal__intro">{{ d.intro }}</p>
        }

        @for (section of d.sections; track $index) {
          <section class="ig-legal__section">
            <h2>{{ section.heading }}</h2>
            @for (para of section.body; track $index) {
              <p>{{ para }}</p>
            }
            @if (section.bullets?.length) {
              <ul>
                @for (bullet of section.bullets; track $index) {
                  <li>{{ bullet }}</li>
                }
              </ul>
            }
          </section>
        }

        <nav class="ig-legal__nav" [attr.aria-label]="'legal.navLabel' | translate">
          <a [routerLink]="siblingLink" class="ig-legal__cross">{{ siblingLabelKey | translate }}</a>
          <a routerLink="/" class="ig-btn ig-btn--ghost">{{ 'common.backToHome' | translate }}</a>
        </nav>
      </article>
    } @else {
      <section class="ig-legal ig-legal--loading" aria-busy="true">
        <p>{{ 'common.loading' | translate }}</p>
      </section>
    }
  `,
  styles: [
    `
      .ig-legal {
        max-width: 760px;
        margin: 0 auto;
        padding: 2.5rem 1rem 1rem;
        color: var(--ink);
        /* Long unbreakable strings / URLs must not force horizontal scroll at 320px. */
        overflow-wrap: break-word;
        word-break: break-word;
      }
      .ig-legal__head {
        margin-bottom: 1.75rem;
        padding-bottom: 1rem;
        border-bottom: 1px solid var(--line);
      }
      .ig-legal h1 {
        font-family: var(--font-display);
        font-size: clamp(1.6rem, 4vw, 2.2rem);
        line-height: 1.15;
        margin: 0 0 0.5rem;
      }
      .ig-legal__eff {
        color: var(--muted);
        font-size: 0.85rem;
        margin: 0;
      }
      .ig-legal__intro {
        font-size: 1.05rem;
        line-height: 1.7;
        color: var(--ink);
        margin: 0 0 1rem;
      }
      .ig-legal__section {
        margin-top: 1.85rem;
      }
      .ig-legal h2 {
        font-size: 1.2rem;
        line-height: 1.3;
        margin: 0 0 0.6rem;
      }
      .ig-legal p {
        line-height: 1.7;
        margin: 0 0 0.8rem;
      }
      .ig-legal ul {
        margin: 0 0 0.8rem;
        padding-left: 1.3rem;
        line-height: 1.7;
      }
      .ig-legal li {
        margin: 0 0 0.35rem;
      }
      .ig-legal__nav {
        margin-top: 2.75rem;
        padding-top: 1.5rem;
        border-top: 1px solid var(--line);
        display: flex;
        gap: 1rem;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
      }
      .ig-legal__cross {
        color: var(--blue-600);
        font-weight: 600;
        text-decoration: none;
      }
      .ig-legal__cross:hover {
        text-decoration: underline;
      }
      .ig-legal--loading p {
        color: var(--muted);
        text-align: center;
        padding: 2.5rem 0;
      }
    `,
  ],
})
export class LegalDocumentComponent implements OnInit {
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  /** Selects the i18n content namespace (`legal.terms` / `legal.privacy`). Bound from route `data`. */
  @Input({ required: true }) doc!: 'terms' | 'privacy';

  /** Resolved document content, or null while loading / if the namespace is missing or malformed. */
  readonly content = signal<LegalDocumentContent | null>(null);

  /** The sibling document, used for the cross-reference link (FR-012). */
  get siblingLink(): string {
    return this.doc === 'terms' ? '/privacy' : '/terms';
  }

  get siblingLabelKey(): string {
    return this.doc === 'terms' ? 'legal.seeAlsoPrivacy' : 'legal.seeAlsoTerms';
  }

  ngOnInit(): void {
    this.load();
    // Re-read on live language switch so the content swaps in place (FR-008).
    this.translate.onLangChange
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }

  private load(): void {
    // get() (Observable) over instant(): fires once the active dictionary has loaded, so a cold
    // deep-link does not render a raw key.
    this.translate
      .get('legal.' + this.doc)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.content.set(this.isValidDocument(value) ? value : null));
  }

  /** Runtime defense: only accept an object with a non-empty `sections` array (VR-5). */
  private isValidDocument(value: unknown): value is LegalDocumentContent {
    if (!value || typeof value !== 'object') {
      return false;
    }
    const candidate = value as Partial<LegalDocumentContent>;
    return Array.isArray(candidate.sections) && candidate.sections.length > 0;
  }
}
