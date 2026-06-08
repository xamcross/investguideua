package com.investguide.investment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.bonds.BondPrice;
import com.investguide.bonds.BondPriceService;
import com.investguide.catalog.Provider;
import com.investguide.catalog.ProviderCategory;
import com.investguide.catalog.RiskLevel;
import com.investguide.catalog.ReturnRange;
import com.investguide.config.AppProperties;
import com.investguide.config.LlmProperties;
import com.investguide.metals.MetalPriceService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parses + validates the advisor's reply into safe {@link InvestmentOption}s (SPECIFICATION §5.4, §8.3,
 * §8.5, §8.6; ticket BE-S6).
 *
 * <p>This is the <b>server-side enforcement</b> that the prompt guardrails (BE-S4) only encourage —
 * non-negotiable per §8.3:
 * <ul>
 *   <li><b>Strict JSON:</b> parse the model text against the §5.4 {@code options} schema. A lenient
 *       single attempt strips an accidental markdown fence; anything still unparseable throws
 *       {@link AdvisorOutputException} (the orchestrator retries once, then fails).</li>
 *   <li><b>Catalog enforcement (anti-hallucination, AC #4/#9):</b> any option whose {@code providerId}
 *       is not an active-catalog slug is dropped. Identity/reference fields ({@code providerName},
 *       {@code category}, {@code minAmount}, {@code sourceUrl}) are taken from the catalog, never the
 *       model — so a jailbreak cannot smuggle in a fake provider, name, or source URL.</li>
 *   <li><b>Empty handling:</b> a model that returns a non-empty list which filters down to zero valid
 *       options is treated as invalid output (hallucination). A model that legitimately returns an empty
 *       array (nothing fits) yields an empty, valid result — not an error (§8.4 rule 2, §8.6).</li>
 *   <li><b>Sanity clamping:</b> {@code expectedReturnPct} is clamped into {@code [0, llm.maxReturnPct]}
 *       (no fantastical returns), and the list is capped at {@code search.maxOptions}.</li>
 * </ul>
 */
@Component
public class AdvisorOutputParser {

    private static final int INSTRUMENT_MAX = 120;
    private static final int LIQUIDITY_MAX = 200;
    private static final int RATIONALE_MAX = 600;

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final LlmProperties llmProperties;
    private final MetalPriceService metalPriceService;
    private final BondPriceService bondPriceService;

    public AdvisorOutputParser(ObjectMapper objectMapper,
                               AppProperties appProperties,
                               LlmProperties llmProperties,
                               MetalPriceService metalPriceService,
                               BondPriceService bondPriceService) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.llmProperties = llmProperties;
        this.metalPriceService = metalPriceService;
        this.bondPriceService = bondPriceService;
    }

    /**
     * @param modelText       raw advisor reply
     * @param activeBySlug    active catalog keyed by slug (the only providers an option may reference)
     * @param requestCurrency the requested deposit currency (default for an option's currency, and the
     *                        baseline the currency-risk disclaimer compares against)
     * @return validated, catalog-grounded, capped options (possibly empty if the model returned none)
     * @throws AdvisorOutputException if the text is not parseable JSON, or a non-empty model list
     *                                contained no in-catalog providers
     */
    public List<InvestmentOption> parse(String modelText, Map<String, Provider> activeBySlug,
                                        SearchCurrency requestCurrency) {
        JsonNode root = readJsonLeniently(modelText);
        JsonNode optionsNode = root.path("options");
        if (!optionsNode.isArray()) {
            throw new AdvisorOutputException("Advisor output missing 'options' array");
        }

        int rawCount = optionsNode.size();
        int maxOptions = appProperties.search().maxOptions();
        List<InvestmentOption> valid = new ArrayList<>();
        int inCatalog = 0; // catalog-grounded candidates BEFORE the metals post-filter (the hallucination gate)
        for (JsonNode node : optionsNode) {
            if (valid.size() >= maxOptions) {
                break;
            }
            String slug = text(node.path("providerId"));
            if (slug == null) {
                continue;
            }
            Provider provider = activeBySlug.get(slug);
            if (provider == null) {
                continue; // out-of-catalog -> dropped (AC #4).
            }
            inCatalog++;
            // Stage 2 (feature 011): a precious-metals option that cannot be grounded with a stored
            // price returns null here and is dropped WITHOUT throwing - it referenced a real provider,
            // so it is not a hallucination (FR-019, SC-010).
            InvestmentOption option = toOption(node, provider, requestCurrency);
            if (option != null) {
                valid.add(option);
            }
        }

        if (rawCount > 0 && inCatalog == 0) {
            // The model proposed only out-of-catalog providers -> treat as invalid (hallucination).
            throw new AdvisorOutputException("Advisor output contained no in-catalog providers");
        }
        return List.copyOf(valid);
    }

    /**
     * Build one option, or {@code null} to DROP it. Drop applies only to a precious-metals option that
     * cannot be grounded with a stored price (feature 011, FR-019) - never to a non-metals option.
     */
    private InvestmentOption toOption(JsonNode node, Provider provider, SearchCurrency requestCurrency) {
        String metal = null;
        Long metalPricePerGramMinor = null;
        if (provider.getCategory() == ProviderCategory.PRECIOUS_METALS) {
            String resolvedMetal = resolveMetal(text(node.path("metal")));
            if (resolvedMetal == null) {
                return null; // missing/invalid metal -> drop (FR-019)
            }
            Optional<Long> price = metalPriceService.currentSalePricePerGramMinor(resolvedMetal);
            if (price.isEmpty()) {
                return null; // no stored price for this metal -> drop (FR-019)
            }
            metal = resolvedMetal;
            metalPricePerGramMinor = price.get();
        }

        String instrument = truncate(text(node.path("instrument")), INSTRUMENT_MAX);
        if (instrument == null) {
            instrument = provider.getName();
        }
        String liquidity = truncate(text(node.path("liquidity")), LIQUIDITY_MAX);
        String rationale = truncate(text(node.path("rationale")), RATIONALE_MAX);
        RiskLevel risk = parseRisk(text(node.path("riskLevel")), provider.getRiskLevel());
        ReturnRange expected = clampReturn(node.path("expectedReturnPct"), provider.getTypicalReturnPct());
        SearchCurrency currency = parseCurrency(text(node.path("currency")), provider, requestCurrency);

        // Bond grounding (feature 012): for a government/military-bond option, ground the model-named
        // ISIN against the stored bondPrices quote. Drop (null) on missing/unknown ISIN or a currency
        // mismatch - the server, not the prompt list, is the guarantee. The stored sell yield REPLACES
        // the model's expected-return range, and the stored sell price is surfaced. The model never
        // supplies these values (FR-003, FR-004, FR-010, FR-011).
        String bondIsin = null;
        Long bondSellPriceMinor = null;
        if (provider.getCategory() == ProviderCategory.MILITARY_BOND
                || provider.getCategory() == ProviderCategory.GOV_BOND) {
            String isin = text(node.path("isin"));
            if (isin == null) {
                return null; // no ISIN -> cannot ground -> drop
            }
            BondPrice bond = bondPriceService.findByIsin(isin).orElse(null);
            if (bond == null) {
                return null; // unknown/unpriced ISIN -> drop
            }
            if (bond.getCurrency() == null || !bond.getCurrency().equalsIgnoreCase(currency.name())) {
                return null; // currency mismatch -> drop (never surface a cross-currency price)
            }
            bondIsin = bond.getIsin();
            bondSellPriceMinor = bond.getSellPriceMinor();
            double y = clamp(bond.getSellYield(), llmProperties.maxReturnPct());
            expected = new ReturnRange(y, y); // real sell yield becomes the authoritative return figure
        }

        // Identity + reference fields come from the catalog, never the model (anti-hallucination, §8.3).
        // The metal/bond prices are grounded server-side too; the model never supplies them (011/012).
        return new InvestmentOption(
                provider.getId(),
                provider.getName(),
                instrument,
                provider.getCategory(),
                currency,
                expected,
                risk,
                provider.getMinAmount(),
                liquidity == null ? "" : liquidity,
                rationale == null ? "" : rationale,
                provider.getSourceUrl(),
                metal,
                metalPricePerGramMinor,
                bondIsin,
                bondSellPriceMinor);
    }

    /** Normalise the model's metal discriminator to {@code GOLD}/{@code SILVER}, or {@code null} if invalid. */
    private static String resolveMetal(String value) {
        if (value == null) {
            return null;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return ("GOLD".equals(upper) || "SILVER".equals(upper)) ? upper : null;
    }

    /**
     * Resolve the option's currency: honour the model only if it names a {@link SearchCurrency} the
     * provider actually supports; otherwise default to the requested currency. This keeps the
     * currency-risk signal (BE-S7) trustworthy — a model can't tag an option with a currency the
     * provider doesn't offer.
     */
    private static SearchCurrency parseCurrency(String value, Provider provider,
                                                SearchCurrency requestCurrency) {
        if (value == null) {
            return requestCurrency;
        }
        SearchCurrency parsed;
        try {
            parsed = SearchCurrency.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return requestCurrency;
        }
        List<String> currencies = provider.getCurrencies();
        if (currencies != null && currencies.stream()
                .anyMatch(c -> c != null && c.trim().equalsIgnoreCase(parsed.name()))) {
            return parsed;
        }
        return requestCurrency;
    }

    /**
     * Clamp the model's return range into {@code [0, maxReturnPct]}; fall back to the provider's typical
     * range when the model omits or garbles the values (§8.5 sanity bound).
     */
    private ReturnRange clampReturn(JsonNode node, ReturnRange fallback) {
        double max = llmProperties.maxReturnPct();
        Double rawMin = number(node.path("min"));
        Double rawMax = number(node.path("max"));
        if (rawMin == null || rawMax == null) {
            if (fallback == null) {
                return new ReturnRange(0.0, 0.0);
            }
            return new ReturnRange(clamp(fallback.min(), max), clamp(fallback.max(), max));
        }
        double lo = clamp(rawMin, max);
        double hi = clamp(rawMax, max);
        if (lo > hi) {
            double tmp = lo;
            lo = hi;
            hi = tmp;
        }
        return new ReturnRange(lo, hi);
    }

    private static double clamp(double v, double max) {
        if (v < 0) {
            return 0;
        }
        return Math.min(v, max);
    }

    private JsonNode readJsonLeniently(String modelText) {
        if (modelText == null || modelText.isBlank()) {
            throw new AdvisorOutputException("Advisor returned empty output");
        }
        try {
            return objectMapper.readTree(modelText);
        } catch (Exception first) {
            // Tolerate a single common deviation: prose/fences around a JSON object. Extract the outermost
            // braces and try once more. This never weakens catalog enforcement (done above) - it only
            // recovers a well-formed object the model wrapped in ```json fences.
            int start = modelText.indexOf('{');
            int end = modelText.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(modelText.substring(start, end + 1));
                } catch (Exception ignored) {
                    // fall through to the failure below
                }
            }
            throw new AdvisorOutputException("Advisor output was not valid JSON");
        }
    }

    private static RiskLevel parseRisk(String value, RiskLevel fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return RiskLevel.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isValueNode()) {
            return null;
        }
        String s = node.asText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Double number(JsonNode node) {
        if (node == null || !node.isNumber()) {
            if (node != null && node.isTextual()) {
                try {
                    return Double.parseDouble(node.asText().trim());
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            return null;
        }
        return node.asDouble();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
