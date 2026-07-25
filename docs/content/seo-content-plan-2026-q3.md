# SEO content plan — Q3 2026

Source: SEO audit + SERP research of 2026-07-24 (see `docs/superpowers/plans/2026-07-24-seo-content-quick-wins.md` for the implementation of wave 1). Volumes are directional (no Ahrefs/GSC connected; US-indexed SERPs). Niche conventions to follow in every piece: year in the title, «X чи Y» comparison framing where it fits, FAQ block targeting the snippet, «станом на [місяць] 2026» qualifiers on all figures, official-source links (mof.gov.ua, bank.gov.ua).

## Shipped in wave 1 (feature 014, 2026-07-24)

- Nav/homepage now link the articles section (was orphaned).
- Expanded: ovdp-war-bonds, buy-ovdp-via-diia, deposit-ovdp-taxes (+FAQ PAA coverage).
- New: sell-ovdp-before-maturity, ovdp-minimum-investment, ovdp-for-fop, gold-vs-ovdp.
- Year-stamped titles on the three comparison/how-to articles.

## Wave 2 — briefs (order = priority)

### 1. Валютні ОВДП: де купити і яка дохідність (`currency-ovdp`)
- **Queries**: валютні ОВДП, ОВДП в доларах, валютні ОВДП де купити. Difficulty: moderate (minfin's articles are aging). Hook: «у Дії їх немає».
- **Outline**: що це і чим відрізняються від гривневих / дохідність проти валютного депозиту (обидва порівняння після податків — валютні ОВДП теж звільнені) / де купити (банки, брокери; мінімальні пороги) / валютні обмеження й виплати / кому підходять. FAQ: мінімальна сума у валюті; чи можна купити за гривню; що з податками.
- **Length**: 900–1,200 слів. Links: uah-vs-usd-savings, ovdp-war-bonds, deposit-ovdp-taxes.

### 2. Податок на депозит 2026: скільки і як рахувати (`deposit-tax-2026`)
- **Queries**: податок на депозит 2026, скільки податку з депозиту. Difficulty: moderate — bank blogs rank with 2025 posts; year-refresh gap.
- **Angle**: чистий калькуляційний розбір (×0,77), таблиця за строками/сумами, і порівняння з ОВДП. Дублює частину deposit-ovdp-taxes → написати як вузьку сторінку під запит, canonical-ні конфлікти виключити різними title/desc і перехресними лінками (не канібалізувати: deposit-ovdp-taxes = порівняння двох інструментів, нова = довідник по депозитному податку).
- **Length**: 700–900 слів + таблиці.

### 3. Як купити золото в Україні: банківські зливки (`buy-gold-ukraine`)
- **Queries**: як купити золото в Україні, банківське золото, інвестиції в золото. Difficulty: moderate (jeweler + aging portal pages rank).
- **Moat**: the site already stores metal prices (feature 011) — embed «ціни банків станом на сьогодні» block; upgrade to live page in feature 016.
- **Outline**: способи (зливки, монети, «паперове» золото) / спреди — головна прихована вартість / як перевірити зливок і документи / зберігання / податки при продажу (обережно, «уточнюйте в банку») / золото в портфелі. FAQ: яка мінімальна вага; де дешевше; що зі старим зливком. Links: gold-vs-ovdp, uah-vs-usd-savings.
- **Length**: 1,000–1,300 слів.

### 4. Як купити ETF з України: Interactive Brokers (`etf-from-ukraine`)
- **Queries**: Interactive Brokers Україна, як купити ETF з України. Difficulty: moderate (personal blogs rank; IB's own pages are RU-language).
- **Outline**: легальність і валютні ліміти НБУ / вибір брокера (IB vs альтернативи — нейтрально) / відкриття рахунку покроково / поповнення з України / перший ETF: UCITS vs US / податки і декларація (це найцінніша частина - queries «податки на дивіденди ETF Україна»). FAQ: мінімальна сума; чи законно; що з податками на дивіденди.
- **Length**: 1,500+ слів. Це найбільший матеріал кварталу; винести податкову частину в окрему статтю, якщо перевалить за 2,000.

### 5. Депозити під час воєнного стану: гарантії ФГВФО (`deposit-guarantee-wartime`)
- **Queries**: гарантування вкладів воєнний стан, чи безпечні депозити. Difficulty: easy-moderate.
- **Outline**: як працює гарантія, що саме покрито, валютні вклади, що робити при банкрутстві банку. Links: deposits-vs-alternatives. FAQ: ліміт відшкодування; чи покриті відсотки.
- **Length**: 700–900 слів.

### 6. Розширення наявних: invest-50000 / invest-100000
- Додати «драбинку ОВДП» і приклад розподілу з реальними ставками; лінк на ovdp-minimum-investment (50k) та etf-from-ukraine (100k). По 300–400 слів кожній.

## Data pages (engineering track)

Feature 015 (spec: `specs/015-ovdp-data-pages/spec.md`): /ovdp/rates, /ovdp/calculator, /ovdp/vs-deposit. Feature 016 candidate: metals price page + auction calendar. These outrank any article for the money queries — schedule ahead of wave-2 articles if engineering capacity allows.

## Monthly refresh routine (recurring, ~1h/month, first business week)

1. Оновити «станом на [місяць] 2026» цифри в: ovdp-war-bonds, buy-ovdp-via-diia, deposit-ovdp-taxes, deposits-vs-alternatives, uah-vs-usd-savings, invest-50k/100k FAQ (ставки з НБУ/каталогу). Одночасно перевірити продуктові факти, що можуть «поплисти»: чи досі в Дії лише гривневі випуски, актуальний список партнерів, ціна «від ~900 грн».
2. Bump `dateModified`/`reviewedOn` тільки там, де цифри реально змінилися (audit A21 = чесний lastmod).
3. Перевірити, що GH Actions price-refresh зелений і (після 015) що Pages rebuild hook спрацював.
4. `npm run seo:all` перед деплоєм.

## Calendar (weeks of Q3 remaining)

| Тиждень | Реліз |
|---|---|
| Jul 28 – Aug 1 | Wave-1 деплой у прод + GSC перевірка індексації нових URL |
| Aug 4 – 8 | currency-ovdp + місячний рефреш |
| Aug 11 – 15 | deposit-tax-2026; старт розробки 015 |
| Aug 18 – 22 | buy-gold-ukraine |
| Aug 25 – 29 | 015 /ovdp/rates + /ovdp/calculator у прод |
| Sep 1 – 5 | etf-from-ukraine + місячний рефреш |
| Sep 8 – 12 | 015 /ovdp/vs-deposit; deposit-guarantee-wartime |
| Sep 15 – 19 | Розширення invest-50k/100k; feature 016 spec (metals page) |
| Sep 22 – 26 | Q3 підсумок: GSC-аналіз позицій, план Q4 |

## Measurement

- Connect GSC API або Ahrefs (marketing-plugin MCP) — зараз позиції не відстежуються.
- KPI: індексація 20/20 URL; поява в топ-10 хоча б за 5 long-tail запитами з хвилі 1 до кінця вересня; CTR головної > 2% на брендові запити.
