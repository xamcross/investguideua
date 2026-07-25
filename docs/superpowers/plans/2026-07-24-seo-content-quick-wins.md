# SEO Content & Quick Wins (feature 014) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the July 2026 SEO audit recommendations: link articles into the site's navigation/homepage, deepen and extend article content, fix minor a11y/meta issues, add four new articles, and write the spec for the ОВДП data-pages feature.

**Architecture:** All content changes are Markdown files under `frontend/src/content/articles/` (the build pipeline regenerates `articles.data.ts`, prerender routes, sitemap, OG images, llms.txt automatically). Navigation/homepage changes are small edits to three standalone Angular components plus i18n JSON. Verification is the existing blocking gate `npm run seo:all` plus Karma tests.

**Tech Stack:** Angular 17 standalone + OnPush, ngx-translate, Markdown+frontmatter article compiler (`tools/seo/build-articles.mjs`), Karma/Jasmine.

## Global Constraints

- Branch: `014-seo-content`, based on `013-seo-improvements` (not main — local main is stale; 013 is deployed).
- Article `title` ≤ **44 chars** (rendered `<title>` appends ` - InvestGuideUA` = 16 chars; audit cap 60). `description` **110–160 chars**. Both enforced by `build-articles.mjs` and `seo-audit.mjs`.
- FAQ entries: `q` ends with `?`, `a` 40–400 chars, ≥2 unique entries per article.
- Every article: ≥1 `## …?` heading; `status: "published"`; every new slug must appear in ≥2 peers' `relatedSlugs` (orphan check `build-articles.mjs:163-172`).
- Touched articles: bump `dateModified` and `reviewedOn` to `2026-07-24`.
- Every new i18n key goes to BOTH `public/i18n/uk.json` and `public/i18n/en.json`.
- The header «Статті» link MUST be outside the `authStatus()` conditionals — prerender runs in the `unknown` auth state and currently emits an empty nav.
- Factual guardrails for content: only facts already established in existing repo articles (ОВДП tax-free; deposit tax 18% ПДФО + 5% ВЗ = 23%; nominal 1000 грн; Дія price ~from 900 грн; yields "орієнтовно 15–16,5%" as of July 2026; partners Sense Bank/Укргазбанк/ICU/КІНТО/bond.ua) or stable mechanics. No new numeric claims. Link official sources (mof.gov.ua, bank.gov.ua) as existing articles do.
- Karma on this machine: no Chrome; run with `CHROME_BIN="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"`.
- CLAUDE.md verification: no `.ps1/.cmd/.bat` files are touched, so the ASCII rule is not in play; the parse/verify gate is `npm run seo:all` + `npm run test:ci`.
- Commit after each task; messages end with the Co-Authored-By/Claude-Session trailer.

---

### Task 1: Branch + header/footer «Статті» links

**Files:**
- Modify: `frontend/src/app/app.component.ts:52-77` (nav template)
- Modify: `frontend/src/app/core/layout/footer.component.ts:42-46` (Company column)
- Modify: `frontend/public/i18n/uk.json:10-22` (`nav.articles`), `frontend/public/i18n/en.json` (same key)
- Test: `frontend/src/app/app.component.spec.ts` (header), new `frontend/src/app/core/layout/footer.component.spec.ts`

**Interfaces:**
- Produces: i18n key `nav.articles` («Статті» / "Articles") reused by Task 2's "all articles" link if needed.

- [ ] **Step 1: Create branch**

```bash
git checkout -b 014-seo-content
```

- [ ] **Step 2: Write failing tests**

In `app.component.spec.ts`, add (following the existing tri-state auth test style in that file):

```ts
it('shows the Articles link for guests, authenticated users AND while auth is unknown (prerender)', () => {
  // for each auth state used by existing tests:
  const link = fixture.nativeElement.querySelector('a[href="/articles"]');
  expect(link).withContext('articles link must render outside auth conditionals').toBeTruthy();
});
```

New `footer.component.spec.ts`:

```ts
it('links to the articles hub', () => {
  const link = fixture.nativeElement.querySelector('a[href="/articles"]');
  expect(link).toBeTruthy();
});
```

- [ ] **Step 3: Run tests, expect FAIL** (`querySelector` returns null)

- [ ] **Step 4: Implement**

`app.component.ts` — insert BEFORE the `@if (auth.authStatus() === 'authenticated')` block (so it renders in every auth state, including prerender's `unknown`):

```html
<a routerLink="/articles" routerLinkActive="is-active" ariaCurrentWhenActive="page"
   (click)="menuOpen.set(false)">{{ 'nav.articles' | translate }}</a>
```

`footer.component.ts` — in the Company column, before editorial-policy:

```html
<a routerLink="/articles">{{ 'nav.articles' | translate }}</a>
```

i18n: `"articles": "Статті"` (uk), `"articles": "Articles"` (en) under `nav`.

- [ ] **Step 5: Run tests, expect PASS; commit** `feat(seo): link articles hub from header and footer nav`

---

### Task 2: Homepage «Останні статті» section

**Files:**
- Modify: `frontend/src/app/features/landing/landing.component.ts` (template between the samples section ending at `:118` and the pricing section at `:120`; class needs `ArticleService` + article list)
- Modify: `frontend/public/i18n/uk.json` + `en.json` (`landing.articlesKicker/articlesTitle/articlesAll`)
- Test: new `frontend/src/app/features/landing/landing.component.spec.ts`

**Interfaces:**
- Consumes: `ArticleService.list(lang)` from `src/app/features/articles/article.service.ts` (pattern: `articles-index.component.ts:59-62`), `LanguageService.current()`.

- [ ] **Step 1: Failing test** — landing renders ≥3 links matching `a[href^="/articles/"]` and one `a[href="/articles"]`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** — component class: inject `ArticleService` + `LanguageService`, expose `latestArticles` = first 3 of `list(lang.current())` sorted by `datePublished` desc. Template section (reuses `ig-card`/kicker idiom):

```html
<section class="ig-articles" aria-labelledby="ig-articles-title">
  <p class="ig-kicker">{{ 'landing.articlesKicker' | translate }}</p>
  <h2 id="ig-articles-title">{{ 'landing.articlesTitle' | translate }}</h2>
  <ul class="ig-arts">
    @for (a of latestArticles; track a.slug; let i = $index) {
      <li [class]="'ig-card ig-art reveal d' + (i + 1)">
        <a [routerLink]="['/articles', a.slug]" class="ig-art__link">
          <h3 class="ig-art__title">{{ a.title }}</h3>
          <p class="ig-muted">{{ a.summary }}</p>
        </a>
      </li>
    }
  </ul>
  <a routerLink="/articles" class="ig-btn ig-btn--ghost">{{ 'landing.articlesAll' | translate }}</a>
</section>
```

i18n uk: kicker «Корисні матеріали», title «Останні статті про інвестиції», all «Усі статті». en: "Guides", "Latest investing articles", "All articles".

- [ ] **Step 4: Run test, expect PASS; commit** `feat(seo): latest-articles section on the landing page`

---

### Task 3: Hero lede keyword, language-toggle a11y, shell meta locale

**Files:**
- Modify: `frontend/public/i18n/uk.json:103` (`landing.lead`), `:3-4` (`lang.toEnglish/toUkrainian`); mirror keys in `en.json`
- Modify: `frontend/src/index.html:15` (fallback meta description)

- [ ] **Step 1:** `landing.lead` (uk) → «Підкажемо, куди вкласти гроші в Україні: вкажіть суму - і отримайте варіанти з перевіреного каталогу банків та провайдерів, простою мовою.» (en mirrored: "We help you decide where to invest money in Ukraine: enter an amount and get options from a vetted catalog of banks and providers, in plain language.")
- [ ] **Step 2:** a11y — accessible name must contain the visible text «UA/EN»: `lang.toEnglish` → "UA/EN - switch language to English"; `lang.toUkrainian` (uk) → «UA/EN - перемкнути мову на українську» (mirror both keys in both files; values differ per file as today).
- [ ] **Step 3:** `src/index.html` description → `«Підбір інвестицій в Україні: депозити, ОВДП (військові облігації), метали та фонди з перевіреного каталогу.»`
- [ ] **Step 4:** Run existing Karma suite (no regressions); commit `fix(seo): keyworded hero lede, lang-toggle a11y name, uk shell meta`

---

### Task 4: Year-stamped titles (3 articles)

**Files:**
- Modify: `frontend/src/content/articles/ovdp-war-bonds.uk.md:4` — title → `"ОВДП 2026: як купити військові облігації"` (40 chars)
- Modify: `frontend/src/content/articles/deposits-vs-alternatives.uk.md` — title → `"Депозит чи ОВДП у 2026: куди вкласти гроші"` (42)
- Modify: `frontend/src/content/articles/uah-vs-usd-savings.uk.md` — title → `"Гривня чи долар у 2026: де зберігати кошти"` (42)
- All three: `dateModified: "2026-07-24"`, `reviewedOn: "2026-07-24"`

- [ ] **Step 1:** Apply edits. **Step 2:** `npm run seo:articles` → expect clean compile (length/uniqueness validated). **Step 3:** Commit `feat(seo): year-stamped titles for high-intent articles`

---

### Task 5: FAQ PAA extensions (verbatim content)

**Files:** the three `.uk.md` files below; bump `dateModified`/`reviewedOn` to `2026-07-24` where not already done.

- [ ] **Step 1:** `ovdp-war-bonds.uk.md` — append to `faq:`

```yaml
  - q: "Чи безпечно купувати військові облігації?"
    a: "Ризик мінімальний: виплати гарантує держава, і Україна жодного разу не допускала дефолту за гривневими ОВДП, зокрема під час повномасштабної війни. Головний практичний ризик - потреба продати папери раніше строку за ринковою ціною."
  - q: "Чи потрібно декларувати дохід від ОВДП?"
    a: "Ні. Дохід від ОВДП звільнений від оподаткування, тому подавати річну декларацію лише через купони чи погашення облігацій не потрібно. Якщо ви подаєте декларацію з інших причин, цей дохід зазначається довідково."
```

- [ ] **Step 2:** `buy-ovdp-via-diia.uk.md` — append:

```yaml
  - q: "Чи можна купити валютні ОВДП через Дію?"
    a: "Ні, у Дії продаються лише гривневі військові облігації. Валютні ОВДП (у доларах чи євро) можна придбати через банки та ліцензованих брокерів - умови й мінімальні суми відрізняються, тож порівняйте кілька пропозицій."
  - q: "Як продати облігації, куплені в Дії?"
    a: "Достроковий продаж оформлюється через партнера, у якого ви купували папери, - банк або брокера на вторинному ринку. Ціна залежить від ринкових умов на момент продажу, тому сума може бути як більшою, так і меншою за вкладену."
```

- [ ] **Step 3:** `deposit-ovdp-taxes.uk.md` — append:

```yaml
  - q: "Як порахувати чистий дохід за депозитом?"
    a: "Помножте нараховані відсотки на 0,77 - саме стільки залишається після утримання 18% ПДФО та 5% військового збору. Наприклад, ставка 16% річних дає близько 12,3% на руки; цю цифру й порівнюйте з дохідністю ОВДП."
  - q: "Чи платить ФОП податки з депозитних відсотків?"
    a: "Відсотки за особистим вкладом підприємця оподатковуються так само, як у будь-якої фізичної особи: банк утримує 18% ПДФО та 5% військового збору при виплаті. До єдиного податку ФОП ці відсотки не включаються."
```

- [ ] **Step 4:** `npm run seo:articles` → clean. Commit `feat(seo): extend article FAQs with PAA question coverage`

---

### Task 6–8: Expand ОВДП-cluster article bodies

**Files:** `ovdp-war-bonds.uk.md`, `buy-ovdp-via-diia.uk.md`, `deposit-ovdp-taxes.uk.md` (bodies only; frontmatter already bumped).

Targets and section outlines (body prose composed at execution under the Global Constraints factual guardrails; keep existing sections/links, add):

- [ ] **Task 6 — ovdp-war-bonds** (~1,000 words): add `## Види ОВДП: гривневі, валютні, "військові"`, `## Банк, брокер чи Дія: де купувати?` (comparison table: канал / мінімальний поріг / кому підходить), `## Як обрати випуск`, expand `## Що врахувати` into risk detail (вторинний ринок, строк, валютний контекст), closing paragraph linking `/search` tool. Commit `feat(seo): deepen ovdp-war-bonds guide`.
- [ ] **Task 7 — buy-ovdp-via-diia** (~900 words): expand steps with sub-details (верифікація, Дія.Підпис, ліміти), add `## Куди приходять купони та погашення?`, `## Типові питання під час покупки` (зарахування 1-3 дні, статуси), `## Валютні ОВДП: чому їх немає в Дії`. Commit `feat(seo): deepen buy-ovdp-via-diia instruction`.
- [ ] **Task 8 — deposit-ovdp-taxes** (~800 words): add `## Валютні вклади: як рахується податок?` (у гривні за курсом, mechanics only), second worked example (50 000 грн), `## Підсумкова таблиця` (інструмент / податок / хто сплачує), expand fund taxation paragraph. Commit `feat(seo): deepen deposit-ovdp-taxes explainer`.

After each: `npm run seo:articles` must stay clean.

---

### Task 9–12: Four new articles

Each = ONE new file `frontend/src/content/articles/<slug>.uk.md` + inbound `relatedSlugs` in ≥2 peers. All: `lang: "uk"`, `status: "published"`, `datePublished/dateModified/reviewedOn: "2026-07-24"`, `reviewedBy: "Редакція InvestGuideUA"`, ≥1 `## …?` heading, ≥2 FAQ, body 600–900 words.

- [ ] **Task 9 — `sell-ovdp-before-maturity`** — title `"Як продати ОВДП достроково: порядок і ціна"` (42); desc «Як продати військові облігації до погашення: через банк чи брокера на вторинному ринку, від чого залежить ціна і коли продаж невигідний.» (~135); keywords `["як продати ОВДП достроково", "продати військові облігації"]`; relatedSlugs `["ovdp-war-bonds", "buy-ovdp-via-diia", "understanding-investment-risk"]`; inbound from `ovdp-war-bonds` + `buy-ovdp-via-diia`. Sections: Чи можна продати ОВДП до погашення? / Як відбувається продаж / Від чого залежить ціна / Коли продавати невигідно / Альтернатива: короткі випуски. FAQ: «Чи втрачу я вже нараховані купони?» (накопичений купонний дохід входить у ціну продажу), «Скільки триває продаж ОВДП?» (залежить від партнера, зазвичай кілька робочих днів). Commit per article.
- [ ] **Task 10 — `ovdp-minimum-investment`** — title `"Мінімальна сума для купівлі ОВДП у 2026"` (39); desc «Скільки грошей потрібно, щоб купити ОВДП: номінал 1000 грн, ціни випусків у Дії від ~900 грн, мінімальні пороги банків і брокерів.» (~125); keywords `["мінімальна сума ОВДП", "скільки коштує ОВДП"]`; relatedSlugs `["ovdp-war-bonds", "buy-ovdp-via-diia", "invest-50000-uah-safely"]`; inbound from `ovdp-war-bonds` + `invest-50000-uah-safely`. Sections: Яка мінімальна сума для ОВДП? / Чому ціна відрізняється від номіналу / Пороги в банках і брокерів / З чого почати з 1000-5000 грн. FAQ: «Чи можна купити пів облігації?» (ні, лише цілі папери), «Чи вигідно купувати одну облігацію?» (так, для знайомства з інструментом; комісії партнера можуть з'їсти частину доходу - перевірте тарифи).
- [ ] **Task 11 — `ovdp-for-fop`** — title `"ОВДП для ФОП: як купувати і що з податками"` (42); desc «Чи може ФОП інвестувати у військові облігації: купівля як фізична особа, звільнення доходу від податків і що з єдиним податком та декларацією.» (~135); keywords `["ОВДП для ФОП", "ФОП військові облігації податки"]`; relatedSlugs `["ovdp-war-bonds", "deposit-ovdp-taxes", "beginner-guide-investing-ukraine"]`; inbound from `deposit-ovdp-taxes` + `ovdp-war-bonds`. Sections: Чи може ФОП купити ОВДП? (так, як фізособа) / Податки: що з єдиним податком (дохід від ОВДП - не підприємницький; звільнений від ПДФО і ВЗ) / Як оформити покупку / Що з декларацією. FAQ: «Чи включається дохід від ОВДП у ліміт ФОП?» (ні, це особистий інвестиційний дохід фізособи, а не виручка ФОП), «З якого рахунку ФОП платити за облігації?» (з особистого рахунку фізособи, не з підприємницького).
- [ ] **Task 12 — `gold-vs-ovdp`** — title `"Золото чи ОВДП: що обрати у 2026 році"` (37); desc «Порівнюємо банківське золото та військові облігації: дохідність, захист від девальвації, ліквідність, спреди й податки - що і коли обирати.» (~135); keywords `["золото чи ОВДП", "інвестиції в золото Україна"]`; relatedSlugs `["ovdp-war-bonds", "uah-vs-usd-savings", "understanding-investment-risk"]`; inbound from `uah-vs-usd-savings` + `understanding-investment-risk`. Sections: Чим відрізняються золото та ОВДП? / Дохідність: фіксований купон проти ціни металу / Ліквідність і спреди / Податки / Кому що підходить (+ це не порада, диверсифікація). FAQ: «Чи захищає золото від девальвації гривні?» (історично так на довгих горизонтах, але ціна коливається і купівля-продаж має спред), «Що ліквідніше - золото чи ОВДП?» (ОВДП з коротким строком передбачуваніші; продаж зливків залежить від банку, стану зливка і спреду). providersReferenced: `["privatbank", "oschadbank"]`.

After all four: run `npm run seo:articles` — orphan check + validations clean.

---

### Task 13: Spec 015 + content plan docs

**Files:**
- Create: `specs/015-ovdp-data-pages/spec.md` — spec for live ОВДП yields page, calculator, «ОВДП чи депозит» comparison (grounded in the existing `bondPrices` collection / features 009+012); covers: public API endpoint for latest per-ISIN prices, three routes with prerender + client-side freshness hydration, title/desc strategy under the 60-char audit cap, Dataset + FAQPage JSON-LD, month-stamp convention, CWV budget, acceptance criteria wired into `seo-audit.mjs`.
- Create: `docs/content/seo-content-plan-2026-q3.md` — content briefs (валютні ОВДП, податок на депозит калькулятор, як купити золото + live-price page, IB/ETF guide, broker comparison, monthly refresh process) + Q3 week-by-week calendar.

- [ ] Write both docs; commit `docs(seo): spec for ОВДП data pages + Q3 content plan`

---

### Task 14: Full verification + reviews

- [ ] **Step 1:** `npm run seo:all` (build → generate → audit) — expect `[audit] PASS`.
- [ ] **Step 2:** `CHROME_BIN=<Edge path> npm run test:ci` — expect all Karma specs green.
- [ ] **Step 3:** Word-count check on built HTML: expanded articles ≥ 800 words rendered; new articles present with FAQ JSON-LD.
- [ ] **Step 4:** Mandatory role sub-agent reviews (CLAUDE.md): QA reviewer (content validation rules, FAQ/JSON-LD correctness, i18n key parity uk/en) and FE-lead reviewer (nav placement outside auth conditionals, OnPush correctness, prerender output contains new links). Apply findings.
- [ ] **Step 5:** Re-run `npm run seo:all` if anything changed; final commit; push `014-seo-content` to origin.
