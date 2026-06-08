---
description: "Task list for feature 012 bond price grounding in investment options"
---

# Tasks: Bond Price Grounding in Investment Options

**Input**: Design documents from `C:\Users\xamcr\InvestGuideUA\specs\012-bond-price-grounding\`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED - the plan enumerates a Test Plan and Constitution V/VI require an actual
parse/compile + test run before "done".

**Organization**: Grounding-only feature. US1 (P1) carries all the code (prompt list + parser branch +
frontend); US2 (P3) is a no-code guardrail (admin-only raw reads stay as feature 009). Shared building
blocks (the new `InvestmentOption` fields and the `BondPriceService` read methods) are Foundational
because both the prompt-listing and the parser depend on them. Reuses feature 009 `bondPrices` storage
and the merged feature 011 two-stage grounding (present in this branch's base).

**Path base**: backend `backend/src/main/java/com/investguide/`, backend tests
`backend/src/test/java/com/investguide/`, frontend `frontend/src/app/`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 / US2

---

## Phase 1: Setup

**Purpose**: Confirm the prerequisite base is present (no new deps/config for this feature).

- [x] T001 Verify this branch's base includes merged feature 011: `AdvisorOutputParser` has the
  two-stage `inCatalog` grounding and `InvestmentOption` has the `metal`/`metalPricePerGramMinor`
  fields (read `backend/src/main/java/com/investguide/investment/AdvisorOutputParser.java` and
  `InvestmentOption.java`). No code change; this de-risks the rebase dependency.

**Checkpoint**: Base confirmed; grounding extension can proceed.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared data-shape and bond read access that both the prompt list and the parser need.

**⚠️ CRITICAL**: Complete before the US1 grounding tasks.

- [x] T002 [P] Add two nullable trailing fields `String bondIsin` and `Long bondSellPriceMinor` to the `InvestmentOption` record (after `metal`, `metalPricePerGramMinor`; boxed so legacy persisted searches read null) in `backend/src/main/java/com/investguide/investment/InvestmentOption.java`
- [x] T003 Update the two `new InvestmentOption(...)` construction sites in `backend/src/test/java/com/investguide/investment/InvestmentSearchServiceTest.java` to append `, null, null` (non-bond options) so the suite compiles (depends on T002)
- [x] T004 [P] Add `Optional<BondPrice> findByIsin(String isin)` (delegates to `repository.findById`) and `List<BondPrice> listForPrompt(String currency)` (`findAll()` filtered to the exact currency string; excludes other currencies incl. EUR) to `backend/src/main/java/com/investguide/bonds/BondPriceService.java`
- [x] T005 [P] Add cases to `backend/src/test/java/com/investguide/bonds/BondPriceServiceTest.java`: `findByIsin` returns the stored record and empty for unknown; `listForPrompt("UAH")` returns only UAH records and excludes USD/EUR (depends on T004)

**Checkpoint**: `mvn -q test` compiles; bond read methods covered.

---

## Phase 3: User Story 1 - Bond options show exact current price and yield (Priority: P1) 🎯 MVP

**Goal**: When the generator presents a `MILITARY_BOND`/`GOV_BOND` option, it carries the exact stored
sell price + sell-yield-as-return for a specific ISIN; ungroundable bond options are dropped without
failing the search.

**Independent Test**: Unit-test `AdvisorOutputParser` with a stubbed `BondPriceService`: a bond option
naming a stored ISIN gets `bondIsin`/`bondSellPriceMinor` and `expectedReturnPct.min==max==storedYield`;
missing/unknown/wrong-currency ISIN is dropped; all-bond-ungroundable yields empty without throwing;
non-bond options are unchanged. Plus `PromptBuilder` lists the stored ISINs within the token budget.

- [x] T006 [US1] Update `PromptBuilder` in `backend/src/main/java/com/investguide/investment/PromptBuilder.java`: inject `BondPriceService`; render an `ALLOWED_BONDS` block in the **userPrompt** (NEVER systemPrompt) listing `BondPriceService.listForPrompt(input.currency().name())` as `isin | maturity | currency | yield`, capped at a constant (e.g. 25); add `"isin"` to the JSON schema block and a rule (include `isin` only for a MILITARY_BOND/GOV_BOND option, copied verbatim from `ALLOWED_BONDS`, never a price/yield; if `ALLOWED_BONDS` empty, return no bond options); change the budget loop to two ordered passes - drop bonds from the tail to empty FIRST, then fall back to provider-tail truncation (`userPrompt` takes the truncated bond list as a parameter)
- [x] T007 [US1] Add cases to `backend/src/test/java/com/investguide/investment/PromptBuilderTest.java`: the prompt contains `ALLOWED_BONDS` with stored ISINs filtered to the request currency and instructs the `isin` field; with many stored bonds the prompt stays `<= maxInputTokens` AND the first provider (`slug=prov-0`) still appears while bond lines are dropped (proves bonds-truncated-before-providers); empty bond set renders the "no bond options" instruction
- [x] T008 [US1] Update `AdvisorOutputParser` in `backend/src/main/java/com/investguide/investment/AdvisorOutputParser.java`: inject `BondPriceService`; in `toOption`, add a bond branch for `MILITARY_BOND`/`GOV_BOND` (parallel to the metals branch) - read `isin`, drop (return null) if missing, if `findByIsin` empty, or if the stored bond currency != the resolved option currency; otherwise set `bondIsin`/`bondSellPriceMinor` and REASSIGN the existing `expected` local to `new ReturnRange(clamp(sellYield,max), clamp(sellYield,max))`; pass the two new fields to the constructor (depends on T002, T004)
- [x] T009 [US1] Add cases to `backend/src/test/java/com/investguide/investment/AdvisorOutputParserTest.java` (stub `BondPriceService`): grounded bond gets `bondSellPriceMinor` `isEqualTo(stored)` exactly and `expectedReturnPct.min==max==clamp(storedYield)` (SC-001/SC-006); model-supplied `expectedReturnPct`/price is ignored/overwritten (FR-010); missing `isin` dropped; unknown ISIN dropped; currency-mismatch dropped (FR-011/SC-007); all-bond-ungroundable returns empty with NO exception (FR-005/SC-003); two bond options with different valid ISINs each keep their own values (US1 scenario 4); non-bond option keeps bond fields null and its own range (FR-006/SC-004); sky-high stored yield clamped to `maxReturnPct`
- [x] T010 [P] [US1] Add optional `bondIsin?: string | null` and `bondSellPriceMinor?: number | null` to the `InvestmentOption` interface in `frontend/src/app/core/investment/investment.models.ts`
- [x] T011 [US1] In `frontend/src/app/features/search/results.component.ts`: render a bond-price fact row (only when `opt.bondIsin && opt.bondSellPriceMinor != null`) using the per-1000-face label, and collapse the expected-return display to a single number when `expectedReturnPct.min === expectedReturnPct.max` (instead of the `min&ndash;max` range) - FR-012 (depends on T010)
- [x] T012 [P] [US1] Add a `results.bondPrice` label that explicitly says "per 1000 face value" to `frontend/public/i18n/en.json` and its Ukrainian equivalent to `frontend/public/i18n/uk.json`

**Checkpoint**: Bond options carry exact grounded values; ungroundable ones drop cleanly; display is correct.

---

## Phase 4: User Story 2 - Administrator still reads raw bond prices unchanged (Priority: P3)

**Goal**: Confirm the change is additive — admin-only raw bond reads behave exactly as feature 009, and
no user-facing raw bond-price surface was introduced.

**Independent Test**: The existing feature-009 `GET /api/v1/bond-prices` ADMIN/USER/anonymous behavior
is unchanged.

- [x] T013 [US2] Confirm no change to `BondPriceController` or `SecurityConfig` bond routes, and that the existing `backend/src/test/java/com/investguide/bonds/BondPriceControllerTest.java` still passes unmodified (covers FR-009/SC-005). No code change expected; if any was made inadvertently, revert it.

**Checkpoint**: Admin read access verified unchanged.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [x] T014 Run the full backend suite `mvn -q test` (justified by the cross-cutting `InvestmentOption`/`AdvisorOutputParser` changes) and report results
- [x] T015 [P] Verification gate (Constitution V): non-ASCII scan of any touched `.ps1`/`.cmd`/`.bat` (expected none here) returns clean; confirm the frontend builds (`npm run build` or lint) for the model/template/i18n edits
- [x] T016 Mandatory multi-role sub-agent review (Constitution VI): at least two role sub-agents (e.g. BE lead + QA/LLM-guardrails), INCLUDING the scan/parse/compile step; apply or report findings before marking the feature done
- [ ] T017 Run quickstart.md validation (admin read shows bonds; a search grounds a bond option; unknown ISIN drops; non-bond unchanged) per `specs/012-bond-price-grounding/quickstart.md` (needs a running backend + populated `bondPrices`)

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; BLOCKS US1. T003 depends on T002; T005 depends on T004.
- **US1 (Phase 3)**: depends on Foundational. T006 depends on T004; T008 depends on T002 + T004;
  T011 depends on T010. T006/T008 both edit different files (PromptBuilder vs AdvisorOutputParser) so
  can proceed in parallel once Foundational is done.
- **US2 (Phase 4)**: independent verification; can run any time (no code).
- **Polish (Phase 5)**: after US1 (and US2) complete.

### Parallel opportunities

- Foundational: T002 and T004 are [P] (different files); their tests (T003 after T002, T005 after T004)
  follow.
- US1: T010 (frontend model) and T012 (i18n) are [P]; T011 follows T010; backend T006 and T008 can run
  in parallel (different files), each followed by its test (T007, T009).

---

## Parallel Example: Foundational building blocks

```bash
# After T001, the two shared building blocks touch different files:
Task: "Add bondIsin/bondSellPriceMinor to InvestmentOption.java"        # T002
Task: "Add findByIsin/listForPrompt to BondPriceService.java"           # T004
```

---

## Implementation Strategy

### MVP (the whole feature)

This feature is essentially one vertical slice (US1). Recommended order:
1. Phase 1 Setup -> Phase 2 Foundational (fields + service methods + their tests).
2. Phase 3 US1: backend grounding (T006/T008 + tests T007/T009), then frontend (T010-T012).
3. Phase 4 US2 verification, then Phase 5 polish (full test run + review + quickstart).

### Notes

- [P] = different files, no incomplete dependencies.
- Money is integer minor units (`bondSellPriceMinor` per 1000 face); the sell yield is a clamped double
  used as the return figure (no money-as-float).
- The server (parser) is the grounding guarantee: ISIN validity AND currency match are enforced
  server-side and drop the option on failure - the prompt list is only steering.
- No new Windows-executed scripts; verification = non-ASCII scan (expect none) + `mvn test` + frontend
  build + two-role review.
- Verify tests fail before implementing; commit after each task or logical group.
