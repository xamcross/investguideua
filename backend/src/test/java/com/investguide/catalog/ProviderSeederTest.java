package com.investguide.catalog;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * X7 / BE-C1 DoD: providers seed with {@code active=true} and valid {@code category} (instrument)
 * enum values, via a {@code $setOnInsert}-only upsert (no-clobber on re-run). The catalog covers
 * every instrument with at least five providers and uses unique slugs. SPECIFICATION §6, §14.
 */
class ProviderSeederTest {

    /** Task scope: at least this many providers must back every instrument. */
    private static final long MIN_PROVIDERS_PER_INSTRUMENT = 5L;

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ProviderSeeder seeder = new ProviderSeeder(mongoTemplate);

    @Test
    void canonicalProvidersAreActiveWithValidCategoryAndCurrencies() {
        List<Provider> all = ProviderSeeder.canonicalProviders();
        assertThat(all).isNotEmpty();
        assertThat(all).allSatisfy(p -> {
            assertThat(p.isActive()).isTrue();
            assertThat(p.getCategory()).isIn((Object[]) ProviderCategory.values());
            assertThat(p.getRiskLevel()).isIn((Object[]) RiskLevel.values());
            assertThat(p.getCurrencies()).isNotEmpty();
            assertThat(p.getMinAmount()).isGreaterThanOrEqualTo(0L);
            assertThat(p.getId()).isNotBlank();
            assertThat(p.getSourceUrl()).startsWith("https://");
            assertThat(p.getTypicalReturnPct()).isNotNull();
        });
        // Slugs are the recommendation identity (BE-S6); they must be unique.
        assertThat(all).extracting(Provider::getId).doesNotHaveDuplicates();
    }

    @Test
    void everyInstrumentHasAtLeastFiveProviders() {
        Map<ProviderCategory, Long> perInstrument = ProviderSeeder.canonicalProviders().stream()
                .collect(Collectors.groupingBy(Provider::getCategory, Collectors.counting()));

        // Each of the thirteen instrument types is represented...
        assertThat(perInstrument).containsOnlyKeys(ProviderCategory.values());
        // ...by at least five providers.
        assertThat(perInstrument.values())
                .allSatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(MIN_PROVIDERS_PER_INSTRUMENT));
    }

    @Test
    void everyWriteIsSetOnInsertOnly() {
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(Provider.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        when(mongoTemplate.remove(any(Query.class), eq(Provider.class)))
                .thenReturn(DeleteResult.acknowledged(0));

        seeder.run(null);

        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(ProviderSeeder.canonicalProviders().size()))
                .upsert(any(Query.class), updates.capture(), eq(Provider.class));

        for (Update update : updates.getAllValues()) {
            assertThat(update.getUpdateObject().keySet()).containsExactly("$setOnInsert");
        }
    }

    @Test
    void removesNonCanonicalRowsBeforeSeeding() {
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(Provider.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        when(mongoTemplate.remove(any(Query.class), eq(Provider.class)))
                .thenReturn(DeleteResult.acknowledged(0));

        seeder.run(null);

        // Authoritative cleanup: a single delete of every row whose _id is NOT a current canonical
        // slug (clears earlier seed generations so the catalog equals canonicalProviders()).
        ArgumentCaptor<Query> removal = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, times(1)).remove(removal.capture(), eq(Provider.class));
        String json = removal.getValue().getQueryObject().toJson();
        assertThat(json).contains("_id").contains("$nin").contains("privatbank-mil-uah");
    }

    @Test
    void everyRowIsCurrencyCoherent() {
        // A row's currencies are either exactly [UAH] or a non-empty subset of {USD, EUR} — never a
        // mix — so minAmount (quote-currency minor units) and the displayed return are unambiguous.
        assertThat(ProviderSeeder.canonicalProviders()).allSatisfy(p -> {
            List<String> c = p.getCurrencies();
            boolean hasUah = c.contains("UAH");
            boolean hasFx = c.contains("USD") || c.contains("EUR");
            assertThat(hasUah && hasFx)
                    .as("provider %s mixes UAH with FX: %s", p.getId(), c)
                    .isFalse();
            assertThat(c).allMatch(x -> x.equals("UAH") || x.equals("USD") || x.equals("EUR"));
            assertThat(p.getMinAmount()).isGreaterThan(0L);
        });
    }
}
