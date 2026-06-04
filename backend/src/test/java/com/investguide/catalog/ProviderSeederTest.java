package com.investguide.catalog;

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

        seeder.run(null);

        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(ProviderSeeder.canonicalProviders().size()))
                .upsert(any(Query.class), updates.capture(), eq(Provider.class));

        for (Update update : updates.getAllValues()) {
            assertThat(update.getUpdateObject().keySet()).containsExactly("$setOnInsert");
        }
    }
}
