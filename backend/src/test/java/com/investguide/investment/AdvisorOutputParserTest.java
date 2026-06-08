package com.investguide.investment;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final AdvisorOutputParser parser = new AdvisorOutputParser(
            mapper,
            InvestmentTestFixtures.appProperties(100_000_000L, 5, 5),
            InvestmentTestFixtures.llmProperties(3000, 40.0),
            metalPriceService);

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
                metalPriceService);
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
    }
}
