import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

/**
 * Sets the document title from a translation KEY carried in each route's `title` (e.g. `title.search`).
 *
 * Replaces Angular's default {@link TitleStrategy} so the tab title is localized and updates live when
 * the user switches language: we re-apply the last route's title key on every `onLangChange`.
 */
@Injectable({ providedIn: 'root' })
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly translate = inject(TranslateService);
  private lastKey: string | undefined;

  constructor() {
    super();
    this.translate.onLangChange.subscribe(() => this.render(this.lastKey));
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.lastKey = this.buildTitle(snapshot);
    this.render(this.lastKey);
  }

  private render(key: string | undefined): void {
    if (!key) {
      return;
    }
    this.translate.get(key).subscribe((value) => this.title.setTitle(String(value)));
  }
}
