import {
  isIndexable,
  normalizePath,
  stripLangPrefix,
  getStaticSeoPage,
  PUBLIC_PAGES,
} from './seo-routes.config';

/** Pure-function unit specs for the SEO route classification (feature 006-seo-optimization). */
describe('seo-routes.config', () => {
  describe('normalizePath', () => {
    it('drops query and fragment', () => {
      expect(normalizePath('/search?amount=100#x')).toBe('/search');
    });
    it('strips trailing slash except root', () => {
      expect(normalizePath('/articles/')).toBe('/articles');
      expect(normalizePath('/')).toBe('/');
    });
  });

  describe('stripLangPrefix', () => {
    it('maps /en to root', () => {
      expect(stripLangPrefix('/en')).toBe('/');
      expect(stripLangPrefix('/en/')).toBe('/');
    });
    it('strips /en/ prefix', () => {
      expect(stripLangPrefix('/en/articles/ovdp')).toBe('/articles/ovdp');
    });
    it('leaves uk (root) paths unchanged', () => {
      expect(stripLangPrefix('/articles')).toBe('/articles');
    });
  });

  describe('isIndexable', () => {
    it('treats public static pages as indexable', () => {
      expect(isIndexable('/')).toBeTrue();
      expect(isIndexable('/articles')).toBeTrue();
      expect(isIndexable('/terms')).toBeTrue();
    });
    it('treats article detail pages as indexable', () => {
      expect(isIndexable('/articles/ovdp-war-bonds')).toBeTrue();
    });
    it('treats private/utility routes as non-indexable', () => {
      for (const p of ['/login', '/search', '/account', '/history/123', '/payments/result']) {
        expect(isIndexable(p)).withContext(p).toBeFalse();
      }
    });
    it('treats unknown routes as non-indexable', () => {
      expect(isIndexable('/nonexistent')).toBeFalse();
    });
  });

  describe('getStaticSeoPage', () => {
    it('returns metadata for known public pages', () => {
      expect(getStaticSeoPage('/')?.titleKey).toBe('seo.home.title');
    });
    it('returns undefined for article detail (handled by component)', () => {
      expect(getStaticSeoPage('/articles/ovdp-war-bonds')).toBeUndefined();
    });
  });

  it('every public page declares title and description keys', () => {
    for (const p of PUBLIC_PAGES) {
      expect(p.titleKey).toMatch(/^seo\./);
      expect(p.descKey).toMatch(/^seo\./);
    }
  });
});
