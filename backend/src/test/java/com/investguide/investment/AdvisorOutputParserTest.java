package com.investguide.investment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.catalog.Provider;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AdvisorOutputParser} — the non-negotiable server-side output enforcement
 * (SPECIFICATION §8.3, §8.5, §8.6; QA1 AC #4, #9). No LLM, no Mongo: raw model text in, validated
 * options out.
 */
class AdvisorOutputParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AdvisorOutputParser parser = new AdvisorOutputParser(
            mapper,
            InvestmentTestFixtures.appProperties(100_000_000L, 5, 5),
            InvestmentTestFixtures.llmProperties(3000, 40.0));

    private Map<String, Provider> catalog() {
        Map<String, Provider> bySlug = new LinkedHashMap<>();
        Provider p = InvestmentTestFixtures.privatbank();
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
                InvestmentTestFixtures.llmProperties(3000, 40.0));
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
}
