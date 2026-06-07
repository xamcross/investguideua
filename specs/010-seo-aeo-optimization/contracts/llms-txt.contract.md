# Contract: `llms.txt`

Generated into the build output root (`dist/investguide-frontend/browser/llms.txt`), served at `https://investguideua.com/llms.txt`. Derived from `seo-manifest.json` (indexable entries only).

## Format (emerging llms.txt convention)

```text
# InvestGuideUA

> Curated, catalog-grounded investment guidance for Ukrainians. Independent, editor-reviewed explainers on deposits, war bonds (OVDP), currency savings, and avoiding scams.

## Guides

- [ОВДП: як інвестувати у військові облігації](https://investguideua.com/articles/ovdp-war-bonds): Що таке ОВДП (військові облігації), яка дохідність, чому дохід не оподатковується...
- [Депозити проти альтернатив](https://investguideua.com/articles/deposits-vs-alternatives): ...

## About

- [Editorial policy](https://investguideua.com/editorial-policy): How InvestGuideUA produces and reviews its content.
- [Contact](https://investguideua.com/contact): How to reach the InvestGuideUA editorial team.
```

## Rules
- **R1**: H1 = site name; one blockquote summary line.
- **R2**: One `## <Section>` per `kind` — articles under `## Guides`, static pages under `## About`.
- **R3**: Each link `- [title](absolute-url): description` from the manifest entry (absolute URLs via `SEO_SITE_ORIGIN`).
- **R4**: Include only `indexable: true` entries. **Zero** private/utility routes (audit A18 enforces).
- **R5**: Regenerated every build from the manifest — no manual edits, no drift (FR-008).
- **R6**: UTF-8, LF. Not a Windows-executed script — non-ASCII (Ukrainian) content is fine here.

## Acceptance
- Fetch `/llms.txt` ⇒ 200, valid structure, covers 100% of indexable pages, leaks no private route (SC-002).
