import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

/**
 * Site footer (standalone, OnPush). Rendered once in the app shell beneath the router outlet, so it
 * appears on every page. Brand + tagline, Product/Legal link groups, copyright, and a "Made in
 * Ukraine" trust mark. All copy via ngx-translate. Group labels are non-heading elements so the
 * footer does not inject extra headings into each page's outline.
 */
@Component({
  selector: 'ig-footer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslateModule],
  template: `
    <footer class="ig-footer">
      <div class="ig-footer__inner">
        <div class="ig-footer__grid">
          <div class="ig-footer__brand">
            <span class="ig-footer__lead">
              <span class="ig-footer__mark" aria-hidden="true"></span>
              <span class="ig-footer__name">InvestGuide<b>UA</b></span>
            </span>
            <p class="ig-footer__about">{{ 'footer.about' | translate }}</p>
          </div>

          <div class="ig-footer__cols">
            <div class="ig-footer__col">
              <p class="ig-footer__h">{{ 'footer.product' | translate }}</p>
              <a routerLink="/search">{{ 'nav.search' | translate }}</a>
              <a routerLink="/providers">{{ 'nav.providers' | translate }}</a>
              <a routerLink="/tokens">{{ 'footer.tokens' | translate }}</a>
            </div>
            <div class="ig-footer__col">
              <p class="ig-footer__h">{{ 'footer.legal' | translate }}</p>
              <a routerLink="/terms">{{ 'footer.terms' | translate }}</a>
              <a routerLink="/privacy">{{ 'footer.privacy' | translate }}</a>
            </div>
          </div>
        </div>

        <div class="ig-footer__base">
          <span>{{ 'footer.rights' | translate }}</span>
          <span>{{ 'footer.made' | translate }} <span aria-hidden="true">&#127482;&#127462;</span></span>
        </div>
      </div>
    </footer>
  `,
  styles: [
    `
      .ig-footer {
        background: var(--navy-900); color: rgba(255, 255, 255, .72);
        border-top: 5px solid; border-image: linear-gradient(90deg, var(--blue-600) 50%, var(--gold-500) 50%) 1;
        margin-top: 2.5rem;
      }
      .ig-footer__inner { max-width: var(--maxw); margin: 0 auto; padding: 3rem 1rem 1.75rem; }
      .ig-footer__grid { display: flex; justify-content: space-between; gap: 2.5rem; flex-wrap: wrap; }
      .ig-footer__brand { max-width: 34ch; }
      .ig-footer__lead { display: inline-flex; align-items: center; gap: .55rem; }
      .ig-footer__mark { width: 26px; height: 26px; border-radius: 8px; transform: rotate(-6deg);
        background: linear-gradient(180deg, var(--blue-600) 0 50%, var(--gold-500) 50% 100%); }
      .ig-footer__name { font-family: var(--font-display); font-weight: 800; font-size: 1.2rem; color: #fff; }
      .ig-footer__name b { color: var(--blue-300); }
      .ig-footer__about { font-size: .9rem; margin: .85rem 0 0; color: rgba(255, 255, 255, .62); }
      .ig-footer__cols { display: flex; gap: 3.5rem; flex-wrap: wrap; }
      .ig-footer__h { font-family: var(--font-mono); font-size: .72rem; letter-spacing: .12em;
        text-transform: uppercase; color: #fff; margin: 0 0 .75rem; font-weight: 700; }
      .ig-footer__col a { display: block; color: rgba(255, 255, 255, .72); font-size: .9rem;
        padding: .25rem 0; text-decoration: none; }
      .ig-footer__col a:hover { color: var(--gold-300); text-decoration: none; }
      .ig-footer__base { margin-top: 2.25rem; padding-top: 1.25rem; border-top: 1px solid rgba(255, 255, 255, .12);
        display: flex; justify-content: space-between; gap: 1rem; flex-wrap: wrap; font-size: .8rem; color: rgba(255, 255, 255, .58); }
    `,
  ],
})
export class FooterComponent {}
