package com.investguide.catalog;

import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * X7 / BE-C1 DoD: providers seed with {@code active=true} and valid {@code category} enum values,
 * via a {@code $setOnInsert}-only upsert (no-clobber on re-run). SPECIFICATION §6, §14.
 */
class ProviderSeederTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ProviderSeeder seeder = new ProviderSeeder(mongoTemplate);

    @Test
    void canonicalProvidersAreActiveWithValidCategoryAndCurrencies() {
        assertThat(ProviderSeeder.canonicalProviders()).isNotEmpty();
        assertThat(ProviderSeeder.canonicalProviders()).allSatisfy(p -> {
            assertThat(p.isActive()).isTrue();
            assertThat(p.getCategory()).isIn((Object[]) ProviderCategory.values());
            assertThat(p.getRiskLevel()).isIn((Object[]) RiskLevel.values());
            assertThat(p.getCurrencies()).isNotEmpty();
            assertThat(p.getMinAmount()).isGreaterThanOrEqualTo(0L);
            assertThat(p.getId()).isNotBlank();
        });
        // The four reconciled §14 banks.
        assertThat(ProviderSeeder.canonicalProviders()).extracting(Provider::getId)
                .containsExactlyInAnyOrder("privatbank", "oschadbank", "otpbank", "monobank");
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
