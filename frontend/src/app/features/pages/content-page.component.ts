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

/** One titled section of a content page. */
interface ContentSection {
  heading: string;
  body?: string[];
  bullets?: string[];
}

/** Shape of a `pages.<doc>` object read from the i18n dictionary. */
interface ContentPage {
  title: string;
  intro?: string;
  sections: ContentSection[];
  /** Contact email (Contact page only) -> rendered as a prominent mailto. */
  email?: string;
}

/**
 * Generic public content page (feature 010-seo-aeo-optimization, US1). Renders the Editorial Policy /
 * About page and the Contact page - the E-E-A-T authoritativeness footprint. Content lives entirely
 * in the i18n dictionaries under `pages.<doc>` (title, optional intro, ordered sections, optional
 * contact email). The route selects the namespace via `data: { doc: 'editorial' | 'contact' }` bound
 * to `@Input() doc` (app-wide `withComponentInputBinding`). Read via `TranslateService.get(...)` (not
 * the pipe/`instant`) so a cold deep-link does not render a raw key; re-read on language switch.
 *
 * No backend: Contact exposes a `mailto:` only (MVP discipline).
 */
@Component({
  selector: 'ig-content-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule],
  template: `
    @if (content(); as d) {
      <article class="ig-page">
        <header class="ig-page__head">
          <h1>{{ d.title }}</h1>
        </header>

        @if (d.intro) {
          <p class="ig-page__intro">{{ d.intro }}</p>
        }

        @if (d.email) {
          <p class="ig-page__contact">
            <a [href]="'mailto:' + d.email" class="ig-btn ig-btn--gold">{{ d.email }}</a>
          </p>
        }

        @for (section of d.sections; track $index) {
          <section class="ig-page__section">
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

        <nav class="ig-page__nav" [attr.aria-label]="'legal.navLabel' | translate">
          <a routerLink="/" class="ig-btn ig-btn--ghost">{{ 'common.backToHome' | translate }}</a>
        </nav>
      </article>
    } @else {
      <section class="ig-page ig-page--loading" aria-busy="true">
        <p>{{ 'common.loading' | translate }}</p>
      </section>
    }
  `,
  styles: [
    `
      .ig-page {
        max-width: 760px;
        margin: 0 auto;
        padding: 2.5rem 1rem 1rem;
        color: var(--ink);
        overflow-wrap: break-word;
        word-break: break-word;
      }
      .ig-page__head {
        margin-bottom: 1.5rem;
        padding-bottom: 1rem;
        border-bottom: 1px solid var(--line);
      }
      .ig-page h1 {
        font-family: var(--font-display);
        font-size: clamp(1.6rem, 4vw, 2.2rem);
        line-height: 1.15;
        margin: 0;
      }
      .ig-page__intro {
        font-size: 1.05rem;
        line-height: 1.7;
        margin: 0 0 1rem;
      }
      .ig-page__contact {
        margin: 0 0 1.25rem;
      }
      .ig-page__section {
        margin-top: 1.75rem;
      }
      .ig-page h2 {
        font-size: 1.2rem;
        line-height: 1.3;
        margin: 0 0 0.6rem;
      }
      .ig-page p {
        line-height: 1.7;
        margin: 0 0 0.8rem;
      }
      .ig-page ul {
        margin: 0 0 0.8rem;
        padding-left: 1.3rem;
        line-height: 1.7;
      }
      .ig-page li {
        margin: 0 0 0.35rem;
      }
      .ig-page__nav {
        margin-top: 2.5rem;
        padding-top: 1.5rem;
        border-top: 1px solid var(--line);
      }
      .ig-page--loading p {
        color: var(--muted);
        text-align: center;
        padding: 2.5rem 0;
      }
    `,
  ],
})
export class ContentPageComponent implements OnInit {
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  /** Selects the i18n content namespace (`pages.editorial` / `pages.contact`). Bound from route data. */
  @Input({ required: true }) doc!: 'editorial' | 'contact';

  readonly content = signal<ContentPage | null>(null);

  ngOnInit(): void {
    this.load();
    this.translate.onLangChange
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }

  private load(): void {
    this.translate
      .get('pages.' + this.doc)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.content.set(this.isValid(value) ? value : null));
  }

  private isValid(value: unknown): value is ContentPage {
    if (!value || typeof value !== 'object') {
      return false;
    }
    const candidate = value as Partial<ContentPage>;
    return Array.isArray(candidate.sections) && candidate.sections.length > 0;
  }
}
