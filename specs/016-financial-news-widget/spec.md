# Feature 016 — Financial news widget (UA + world RSS headlines)

Status: **draft** (spec approved in brainstorming session 2026-07-26)
Depends on: nothing hard; follows API/controller conventions from 015 (public `/api/v1` read endpoints, cache headers) and the "never blank the collection" ingest principle from 011.

## Why

Engagement/freshness: give visitors a reason to return and make the site feel alive. The site's
public pages are otherwise static between data refreshes; a headlines widget adds daily-changing
content at near-zero operational cost.

Explicitly **not** goals: SEO (republished RSS headlines are thin/duplicate content — the widget is
client-rendered and not part of the prerendered SEO surface), and advisor/LLM grounding (candidate
future feature; the stored `newsItems` collection would be reusable for it).

## What

A **headlines-only news widget** shown on two pages — the landing page (`/`) and the search page
(`/search`) — with two side-by-side lists (stacked on mobile):

- **Україна** — Ukrainian financial news, headlines in Ukrainian.
- **Світові ринки** — world financial/markets news, headlines in English.

Headlines are displayed as-is in their source language (no translation). Each item links out to the
source article (`target="_blank" rel="noopener"`) and shows the source name and a localized relative
time. There is **no separate `/news` page** and no nav change.

Freshness target: headlines at most ~1 hour old while the site has traffic.

### Architecture decision

**On-demand backend fetch with stale-while-revalidate Mongo cache.** Rejected alternatives:

- *GH Actions hourly cron → ingest endpoint* (bonds/metals pattern): ~720+ CI minutes/month,
  best-effort scheduling makes the 1-hour target marginal, three moving parts vs one service.
  The bonds/metals cadence is daily; hourly is a different cost regime.
- *Frontend-side fetch or build-time snapshot*: CORS-blocked / day-stale respectively.

The backend is single-instance (Fly, scales to zero), which makes the in-process refresh guard
trivial and means "no traffic" harmlessly implies "no refresh".

### FR-1 `newsItems` storage

- Mongo collection `newsItems`: `_id` = SHA-256 hex of the item link (dedupe/upsert key),
  `category` (`UKRAINE` | `WORLD`), `sourceName`, `title`, `link`, `publishedAt` (Instant),
  `fetchedAt` (Instant).
- Headlines and links **only** — no article body is stored or displayed (standard RSS use:
  headline + attribution + link out; licensing-safe).
- On each successful refresh, each category is pruned to the newest 20 items by `publishedAt`.
- A failed or empty fetch **never deletes stored items** (same principle as metals ingest).

### FR-2 Feed configuration (`NewsProperties`)

- `@ConfigurationProperties` under `app.news`: per category an ordered list of
  `{ sourceName, url }` feeds, plus `refresh-ttl` (default 30 min), per-feed HTTP timeout
  (default 5 s), `keep-per-category` (default 20), `serve-per-category` (default 8).
- Default sources (verify liveness during implementation; swapping a dead feed is a
  properties-only change):
  - UKRAINE: Економічна правда (`epravda.com.ua/rss/`), Minfin (`minfin.com.ua` RSS).
  - WORLD: CNBC Top News, MarketWatch Top Stories.
- Feed URLs come only from configuration — no user input reaches the fetcher (no SSRF surface).

### FR-3 Refresh service (stale-while-revalidate)

- On each API read: serve current Mongo contents immediately; if the newest `fetchedAt` is older
  than `refresh-ttl`, trigger an async refresh on a dedicated single-thread executor, guarded by an
  in-flight flag (`AtomicBoolean`) so concurrent stale reads cause exactly one refresh.
- Refresh fetches each configured feed **independently**: one feed failing (timeout, HTTP error,
  malformed XML) does not block other feeds or the other category.
- Parsing via **Rome** (`com.rometools:rome`, current release). Normalization per item: strip
  HTML/CDATA from titles, collapse whitespace, cap title length (200 chars), require non-blank
  title + absolute http(s) link + parseable published date — otherwise skip the item.
- Upsert by `_id` (link hash): repeated fetches never duplicate; items dropped by a source but
  still within the newest 20 remain until pruned by newer items.
- Structured result log per refresh: `news_refresh accepted=… rejected=… failedFeeds=…`.
- The very first refresh (empty collection) is triggered by the first API read; that response
  returns empty lists and the widget stays hidden until data lands (acceptable cold-start).

### FR-4 Public API

- `GET /api/v1/news/latest` → `{ "ukraine": [ … ], "world": [ … ] }`, each list the newest
  `serve-per-category` (8) items: `{ sourceName, title, link, publishedAt }`.
- Read-only, unauthenticated, no token cost. `Cache-Control: public, max-age=300` via
  `CacheControl` (015 convention). Empty collection → empty lists, HTTP 200, never an error.
- The endpoint never performs a synchronous feed fetch — response time is a Mongo read.

### FR-5 Frontend widget

- Standalone `NewsWidgetComponent` (`features/news/news-widget.component.ts`) + `NewsService`
  calling FR-4. Self-contained: no inputs, fetches on init. Placement: on the landing page as a
  section after the existing marketing/data sections (before the footer); on the search page below
  the search form/results area. Public endpoint, so the logged-out landing placement works
  unchanged.
- Card titled via i18n (`uk`: «Фінансові новини», `en`: "Financial news"); section labels
  «Україна» / «Світові ринки» translated; relative timestamps localized to the active UI
  language. Shows 5 items per list (API returns 8; surplus reserved for a future "show more").
- Skeleton while loading. If the request errors **or both lists are empty, the widget renders
  nothing** — no error state, no retry UI. A missing news card is better than a broken one.
- Styling follows the existing landing card visual language (redesign tokens).

## Non-goals

- Separate `/news` page, nav entry, or prerendered/SEO treatment.
- Translation of headlines; storing/showing article bodies or images.
- Feeding news into the advisor/LLM flow (future candidate; data model already suits it).
- Admin UI for feed management (config file/env is enough).

## Acceptance

1. `GET /api/v1/news/latest` returns both lists with the exact `Cache-Control: public, max-age=300`
   header; empty collection yields `200 {"ukraine":[],"world":[]}`.
2. Backend tests cover: normalizer against RSS fixtures (valid, malformed XML, HTML-laden titles,
   missing dates/links), stale-vs-fresh refresh triggering (exactly one refresh under concurrent
   stale reads), failed fetch retaining stale items, prune-to-20, upsert idempotency. `mvn test`
   green with feed HTTP mocked (no network in tests).
3. Frontend tests cover: both lists rendered from a stubbed response, widget absent on error and
   on empty payload, links carry `target="_blank" rel="noopener"`. `ng test` green.
4. With the backend running and real feeds reachable, the widget shows current headlines on `/`
   and `/search`, and a second page load within 30 minutes does not re-fetch feeds (log shows no
   second `news_refresh`).
