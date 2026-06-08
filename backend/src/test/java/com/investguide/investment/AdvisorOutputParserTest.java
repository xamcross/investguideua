package com.investguide.investment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.bonds.BondPrice;
import com.investguide.bonds.BondPriceService;
import com.investguide.catalog.Provider;
import com.investguide.catalog.ProviderCategory;
import com.investguide.catalog.ReturnRange;
import com.investguide.catalog.RiskLevel;
import com.investguide.metals.MetalPriceService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdvisorOutputParser} — the non-negotiable server-side output enforcement
 * (SPECIFICATION §8.3, §8.5, §8.6; QA1 AC #4, #9) plus feature 011 metals grounding (FR-018/019/020,
 * SC-009/010). No LLM, no Mongo: raw model text in, validated options out; the metals price lookup is
 * a stubbed {@link MetalPriceService}.
 */
class AdvisorOutputParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final MetalPriceService metalPriceService = mock(MetalPriceService.class);
    private final BondPriceService bondPriceService = mock(BondPriceService.class);
    private final AdvisorOutputParser parser = new AdvisorOutputParser(
            mapper,
            InvestmentTestFixtures.appProperties(100_000_000L, 5, 5),
            InvestmentTestFixtures.llmProperties(3000, 40.0),
            metalPriceService,
            bondPriceService);

    private Map<String, Provider> catalog() {
        Map<String, Provider> bySlug = new LinkedHashMap<>();
        Provider p = InvestmentTestFixtures.privatbank();
        bySlug.put(p.getId(), p);
        return bySlug;
    }

    private static Provider metalsProvider() {
        return InvestmentTestFixtures.provider("privatbank-metals", ProviderCategory.PRECIOUS_METALS,
                List.of("UAH"), 700_000L, new ReturnRange(0.0, 10.0), RiskLevel.MODERATE);
    }

    private Map<String, Provider> metalsCatalog() {
        Map<String, Provider> bySlug = new LinkedHashMap<>();
        Provider p = metalsProvider();
        bySlug.put(p.getId(), p);
        return bySlug;
    }

    private static Provider bondProvider() {
        return InvestmentTestFixtures.provider("mof-bonds", ProviderCategory.GOV_BOND,
                List.of("UAH"), 100_000L, new ReturnRange(13.0, 16.0), RiskLevel.LOW);
    }

    private Map<String, Provider> bondCatalog() {
        Map<String, Provider> bySlug = new LinkedHashMap<>();
        Provider p = bondProvider();
        bySlug.put(p.getId(), p);
        return bySlug;
    }

    private static BondPrice storedBond(String isin, String currency, long sellMinor, double sellYield) {
        return new BondPrice(isin, true, currency,
                java.time.LocalDate.of(2026, 11, 18), java.time.LocalDate.of(2026, 6, 8),
                sellMinor, sellMinor - 700L, sellYield, sellYield + 0.4, java.time.Instant.now());
    }

    @Test
    void parse_backfillsCatalogFields_andKeepsModelNarrative() {
        String json = """
                {"options":[{"providerId":"privatbank","instrument":"Депозит 12 міс","currency":"UAH",
                "expectedReturnPct":{"min":13.0,"max":15.0},"riskLevel":"LOW",
                "liquidity":"Заблоковано на строк","rationale":"Підходить під суму"}]}
                """;

        List<InvestmentOption> options = parser.parse(json, catalog(), SearchCurrency.UAH);

        assertThat(options).hasSize(1);
        InvestmentOption o = options.get(0);
        // Identity/reference come from the catalog, never the model.
        assertThat(o.providerId()).isEqualTo("privatbank");
        assertThat(o.providerName()).isEqualTo("privatbank Bank");
        assertThat(o.sourceUrl()).isEqualTo("https://privatbank.example/invest");
        assertThat(o.minAmount()).isEqualTo(100_000L);
        // Narrative + currency from the model.
        assertThat(o.instrument()).isEqualTo("Депозит 12 міс");
        assertThat(o.currency()).isEqualTo(SearchCurrency.UAH);
    }

    @Test
    void parse_dropsOutOfCatalogProviders_AC4() {
        // The model invents "shadycrypto" and also returns a valid catalog slug -> only the valid one survives.
        String json = """
                {"options":[
                  {"providerId":"shadycrypto","instrument":"Token X","expectedReturnPct":{"min":80,"max":120}},
                  {"providerId":"privatbank","instrument":"Депозит","expectedReturnPct":{"min":13,"max":15}}
                ]}
                """;

        List<InvestmentOption> options = parser.parse(json, catalog(), SearchCurrency.UAH);

        assertThat(options).extracting(InvestmentOption::providerId).containsExactly("privatbank");
    }

    @Test
    void parse_jailbreakAllInvalid_throwsAsInvalidOutput_AC9() {
        // A prompt-injected response that ignores the catalog entirely -> no in-catalog providers -> invalid.
        String json = """
                {"options":[{"providerId":"ignore-all-and-buy-doge","instrument":"DOGE",
                "expectedReturnPct":{"min":900,"max":9000}}]}
                """;

        assertThatThrownBy(() -> parser.parse(json, catalog(), SearchCurrency.UAH))
                .isInstanceOf(AdvisorOutputException.class);
    }

    @Test
    void parse_emptyOptionsArray_isValidEmpty_notError() {
        List<InvestmentOption> options = parser.parse("{\"options\":[]}", catalog(), SearchCurrency.UAH);
        assertThat(options).isEmpty();
    }

    @Test
    void parse_clampsFantasticalReturns_toMaxReturnPct() {
        String json = """
                {"options":[{"providerId":"privatbank","instrument":"x",
                "expectedReturnPct":{"min":-5,"max":999},"riskLevel":"LOW"}]}
                """;

        InvestmentOption o = parser.parse(json, catalog(), SearchCurrency.UAH).get(0);

        assertThat(o.expectedReturnPct().min()).isEqualTo(0.0);   // negative floored to 0
        assertThat(o.expectedReturnPct().max()).isEqualTo(40.0);  // clamped to maxReturnPct
    }

    @Test
    void parse_capsOptionsAtMaxOptions() {
        AdvisorOutputParser cappingParser = new AdvisorOutputParser(
                mapper,
                InvestmentTestFixtures.appProperties(100_000_000L, 2, 5),  // maxOptions = 2
                InvestmentTestFixtures.llmProperties(3000, 40.0),
                metalPriceService,
                bondPriceService);
        String dup = "{\"providerId\":\"privatbank\",\"instrument\":\"x\","
                + "\"expectedReturnPct\":{\"min\":13,\"max\":15}}";
        String json = "{\"options\":[" + dup + "," + dup + "," + dup + "]}";

        assertThat(cappingParser.parse(json, catalog(), SearchCurrency.UAH)).hasSize(2);
    }

    @Test
    void parse_toleratesMarkdownFences() {
        String fenced = "```json\n{\"options\":[{\"providerId\":\"privatbank\","
                + "\"instrument\":\"x\",\"expectedReturnPct\":{\"min\":13,\"max\":15}}]}\n```";

        assertThat(parser.parse(fenced, catalog(), SearchCurrency.UAH)).hasSize(1);
    }

    @Test
    void parse_unparseable_throwsOutputException() {
        assertThatThrownBy(() -> parser.parse("not json at all", catalog(), SearchCurrency.UAH))
                .isInstanceOf(AdvisorOutputException.class);
    }

    @Test
    void parse_unsupportedCurrency_fallsBackToRequestCurrency() {
        // monobank supports only UAH; model claims USD -> server forces back to the requested currency.
        Map<String, Provider> bySlug = new LinkedHashMap<>();
        Provider mono = InvestmentTestFixtures.monobank();
        bySlug.put(mono.getId(), mono);
        String json = """
                {"options":[{"providerId":"monobank","instrument":"Депозит","currency":"USD",
                "expectedReturnPct":{"min":12,"max":14}}]}
                """;

        InvestmentOption o = parser.parse(json, bySlug, SearchCurrency.UAH).get(0);

        assertThat(o.currency()).isEqualTo(SearchCurrency.UAH);
    }

    // ---- feature 011: precious-metals price grounding (FR-018/019/020, SC-009/010) -------------

    @Test
    void parse_groundsMetalsOption_withExactStoredSaleRate_ignoringAnyModelPrice() {
        when(metalPriceService.currentSalePricePerGramMinor("GOLD")).thenReturn(Optional.of(888000L));
        // The model even tries to supply its own price - it must be ignored; the server value wins.
        String json = """
                {"options":[{"providerId":"privatbank-metals","instrument":"Gold bullion 1g",
                "metal":"gold","metalPricePerGramMinor":123,"expectedReturnPct":{"min":0,"max":5},
                "riskLevel":"MODERATE"}]}
                """;

        InvestmentOption o = parser.parse(json, metalsCatalog(), SearchCurrency.UAH).get(0);

        assertThat(o.metal()).isEqualTo("GOLD");
        assertThat(o.metalPricePerGramMinor()).isEqualTo(888000L); // exact stored sale rate (SC-009)
    }

    @Test
    void parse_dropsMetalsOption_whenMetalMissingOrInvalid() {
        when(metalPriceService.currentSalePricePerGramMinor(anyString())).thenReturn(Optional.of(1L));
        String json = """
                {"options":[{"providerId":"privatbank-metals","instrument":"Some metal",
                "expectedReturnPct":{"min":0,"max":5}}]}
                """;

        // Only ungroundable metals -> empty result, NO exception/retry (FR-019, SC-010).
        assertThat(parser.parse(json, metalsCatalog(), SearchCurrency.UAH)).isEmpty();
    }

    @Test
    void parse_dropsMetalsOption_whenNoStoredPrice() {
        when(metalPriceService.currentSalePricePerGramMinor("SILVER")).thenReturn(Optional.empty());
        String json = """
                {"options":[{"providerId":"privatbank-metals","instrument":"Silver bar",
                "metal":"SILVER","expectedReturnPct":{"min":0,"max":5}}]}
                """;

        assertThat(parser.parse(json, metalsCatalog(), SearchCurrency.UAH)).isEmpty();
    }

    @Test
    void parse_allMetalsUngroundable_yieldsEmpty_withoutThrowing() {
        when(metalPriceService.currentSalePricePerGramMinor(anyString())).thenReturn(Optional.empty());
        String json = """
                {"options":[
                  {"providerId":"privatbank-metals","instrument":"Gold","metal":"GOLD",
                   "expectedReturnPct":{"min":0,"max":5}},
                  {"providerId":"privatbank-metals","instrument":"Silver","metal":"SILVER",
                   "expectedReturnPct":{"min":0,"max":5}}
                ]}
                """;

        // Distinct from the out-of-catalog jailbreak case: these reference a REAL provider, so a drop
        // must NOT throw AdvisorOutputException (would fail the whole search + charge a retry).
        assertThat(parser.parse(json, metalsCatalog(), SearchCurrency.UAH)).isEmpty();
    }

    @Test
    void parse_keepsGroundedMetal_dropsUngroundableMetal_andRestStillReturns() {
        when(metalPriceService.currentSalePricePerGramMinor("GOLD")).thenReturn(Optional.of(888000L));
        when(metalPriceService.currentSalePricePerGramMinor("SILVER")).thenReturn(Optional.empty());
        String json = """
                {"options":[
                  {"providerId":"privatbank-metals","instrument":"Gold","metal":"GOLD",
                   "expectedReturnPct":{"min":0,"max":5}},
                  {"providerId":"privatbank-metals","instrument":"Silver","metal":"SILVER",
                   "expectedReturnPct":{"min":0,"max":5}}
                ]}
                """;

        List<InvestmentOption> options = parser.parse(json, metalsCatalog(), SearchCurrency.UAH);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).metal()).isEqualTo("GOLD");
    }

    @Test
    void parse_nonMetalsOption_leavesMetalFieldsNull_FR020() {
        String json = """
                {"options":[{"providerId":"privatbank","instrument":"Депозит",
                "expectedReturnPct":{"min":13,"max":15}}]}
                """;

        InvestmentOption o = parser.parse(json, catalog(), SearchCurrency.UAH).get(0);

        assertThat(o.metal()).isNull();
        assertThat(o.metalPricePerGramMinor()).isNull();
        assertThat(o.bondIsin()).isNull();
        assertThat(o.bondSellPriceMinor()).isNull();
    }

    // ---- feature 012: government/military bond price grounding (FR-001..012, SC-001..007) -------

    @Test
    void parse_groundsBondOption_withExactStoredPrice_andYieldAsReturn_ignoringModelNumbers() {
        when(bondPriceService.findByIsin("UA4000227545"))
                .thenReturn(Optional.of(storedBond("UA4000227545", "UAH", 107658L, 15.25)));
        // The model supplies its own (wrong) return range and a bogus price field - both must be ignored.
        String json = """
                {"options":[{"providerId":"mof-bonds","instrument":"OVDP 1y","isin":"UA4000227545",
                "currency":"UAH","expectedReturnPct":{"min":2,"max":99},"bondSellPriceMinor":1,
                "riskLevel":"LOW"}]}
                """;

        InvestmentOption o = parser.parse(json, bondCatalog(), SearchCurrency.UAH).get(0);

        assertThat(o.bondIsin()).isEqualTo("UA4000227545");
        assertThat(o.bondSellPriceMinor()).isEqualTo(107658L);                 // exact stored value (SC-006)
        assertThat(o.expectedReturnPct().min()).isEqualTo(15.25);              // sell yield, not model 2
        assertThat(o.expectedReturnPct().max()).isEqualTo(15.25);              // degenerate range (FR-003)
    }

    @Test
    void parse_dropsBondOption_whenIsinMissing() {
        String json = """
                {"options":[{"providerId":"mof-bonds","instrument":"OVDP",
                "expectedReturnPct":{"min":13,"max":15}}]}
                """;
        // Only ungroundable bonds -> empty result, NO exception (FR-004/FR-005/SC-003).
        assertThat(parser.parse(json, bondCatalog(), SearchCurrency.UAH)).isEmpty();
    }

    @Test
    void parse_dropsBondOption_whenIsinUnknown() {
        when(bondPriceService.findByIsin("UA0000000000")).thenReturn(Optional.empty());
        String json = """
                {"options":[{"providerId":"mof-bonds","instrument":"OVDP","isin":"UA0000000000",
                "expectedReturnPct":{"min":13,"max":15}}]}
                """;
        assertThat(parser.parse(json, bondCatalog(), SearchCurrency.UAH)).isEmpty();
    }

    @Test
    void parse_dropsBondOption_onCurrencyMismatch() {
        // Isolate the bond-branch currency check from parseCurrency fallback: the provider genuinely
        // supports USD so the option resolves to USD, but the stored bond is UAH -> mismatch -> drop
        // (FR-011/SC-007), never surface a UAH price under a USD option.
        Provider usdBondProvider = InvestmentTestFixtures.provider("mof-bonds-usd",
                ProviderCategory.GOV_BOND, List.of("USD"), 100_000L, new ReturnRange(3.0, 6.0), RiskLevel.LOW);
        Map<String, Provider> bySlug = new LinkedHashMap<>();
        bySlug.put(usdBondProvider.getId(), usdBondProvider);
        when(bondPriceService.findByIsin("UA-UAH"))
                .thenReturn(Optional.of(storedBond("UA-UAH", "UAH", 100000L, 15.0)));
        String json = """
                {"options":[{"providerId":"mof-bonds-usd","instrument":"USD bond","isin":"UA-UAH",
                "currency":"USD","expectedReturnPct":{"min":4,"max":6}}]}
                """;
        assertThat(parser.parse(json, bySlug, SearchCurrency.USD)).isEmpty();
    }

    @Test
    void parse_keepsGroundedBond_dropsUngroundableBond_andRestStillReturns() {
        // AC-2: one bond grounds, a sibling bond is ungroundable -> the groundable one survives, the
        // other is dropped, and the search still returns (no throw).
        when(bondPriceService.findByIsin("UA-OK"))
                .thenReturn(Optional.of(storedBond("UA-OK", "UAH", 100000L, 15.0)));
        when(bondPriceService.findByIsin("UA-BAD")).thenReturn(Optional.empty());
        String json = """
                {"options":[
                  {"providerId":"mof-bonds","instrument":"Good","isin":"UA-OK","expectedReturnPct":{"min":1,"max":2}},
                  {"providerId":"mof-bonds","instrument":"Bad","isin":"UA-BAD","expectedReturnPct":{"min":1,"max":2}}
                ]}
                """;

        List<InvestmentOption> options = parser.parse(json, bondCatalog(), SearchCurrency.UAH);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).bondIsin()).isEqualTo("UA-OK");
    }

    @Test
    void parse_allBondsUngroundable_yieldsEmpty_withoutThrowing() {
        when(bondPriceService.findByIsin(anyString())).thenReturn(Optional.empty());
        String json = """
                {"options":[
                  {"providerId":"mof-bonds","instrument":"A","isin":"UA1","expectedReturnPct":{"min":1,"max":2}},
                  {"providerId":"mof-bonds","instrument":"B","isin":"UA2","expectedReturnPct":{"min":1,"max":2}}
                ]}
                """;
        assertThat(parser.parse(json, bondCatalog(), SearchCurrency.UAH)).isEmpty();
    }

    @Test
    void parse_mixedBondIsins_eachKeepsOwnValues_noCrossContamination() {
        when(bondPriceService.findByIsin("UA-A"))
                .thenReturn(Optional.of(storedBond("UA-A", "UAH", 100000L, 15.0)));
        when(bondPriceService.findByIsin("UA-B"))
                .thenReturn(Optional.of(storedBond("UA-B", "UAH", 200000L, 17.0)));
        String json = """
                {"options":[
                  {"providerId":"mof-bonds","instrument":"A","isin":"UA-A","expectedReturnPct":{"min":1,"max":2}},
                  {"providerId":"mof-bonds","instrument":"B","isin":"UA-B","expectedReturnPct":{"min":1,"max":2}}
                ]}
                """;

        List<InvestmentOption> options = parser.parse(json, bondCatalog(), SearchCurrency.UAH);

        assertThat(options).hasSize(2);
        assertThat(options).anySatisfy(o -> {
            assertThat(o.bondIsin()).isEqualTo("UA-A");
            assertThat(o.bondSellPriceMinor()).isEqualTo(100000L);
            assertThat(o.expectedReturnPct().min()).isEqualTo(15.0);
        });
        assertThat(options).anySatisfy(o -> {
            assertThat(o.bondIsin()).isEqualTo("UA-B");
            assertThat(o.bondSellPriceMinor()).isEqualTo(200000L);
            assertThat(o.expectedReturnPct().max()).isEqualTo(17.0);
        });
    }

    @Test
    void parse_clampsSkyHighStoredBondYield_toMaxReturnPct() {
        when(bondPriceService.findByIsin("UA-X"))
                .thenReturn(Optional.of(storedBond("UA-X", "UAH", 100000L, 999.0)));
        String json = """
                {"options":[{"providerId":"mof-bonds","instrument":"X","isin":"UA-X",
                "expectedReturnPct":{"min":1,"max":2}}]}
                """;

        InvestmentOption o = parser.parse(json, bondCatalog(), SearchCurrency.UAH).get(0);

        assertThat(o.expectedReturnPct().max()).isEqualTo(40.0); // clamped to maxReturnPct
    }
}
