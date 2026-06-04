# Editorial acceptance checklist (feature 006-seo-optimization, FR-024)

Every article must pass this checklist before `status: published`. Record the reviewer in the
`reviewedBy` frontmatter field and the date in `reviewedOn`.

- [ ] **Original** - written for InvestGuideUA, not copied from another source.
- [ ] **Accurate** - facts, instruments and provider names are correct and current; a knowledgeable
      reviewer signed off.
- [ ] **Intent match** - the body genuinely answers the search intent of `primaryTopic`.
- [ ] **Catalog-grounded** - every provider in `providersReferenced` exists in the curated catalog;
      no off-catalog providers are recommended (FR-025).
- [ ] **No individualized advice** - framed as information/examples, never "you should buy X"
      professional financial advice (Constitution IV).
- [ ] **Source-linked figures** - any rate/yield/number links to an official source (Minfin, NBU,
      or the provider) rather than a bare claim (FR-026).
- [ ] **Length bounds** - `title` <= 60 chars; `description` 110-160 chars.
- [ ] **Dates** - `datePublished` and `dateModified` set; bump `dateModified` on any content change.
- [ ] **Related** - `relatedSlugs` point to published articles in the same language.

The build-time pipeline (`npm run seo:articles`) hard-fails on the mechanical rules (lengths,
sign-off presence, catalog membership, related-slug resolution); the human judgement items above
are the reviewer's responsibility.
