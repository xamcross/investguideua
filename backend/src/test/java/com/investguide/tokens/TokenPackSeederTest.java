package com.investguide.tokens;

import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * X7 structural tests for the token-pack seeder (SPECIFICATION §9.1; ticket X7).
 *
 * <p>Idempotent "no-clobber on re-run" is implemented via {@code $setOnInsert}-only upserts keyed
 * by {@code _id}. Proving the no-overwrite behavior end-to-end needs a real Mongo (Testcontainers),
 * which is out of scope for the unit suite; instead we assert the <i>mechanism</i>: every write is
 * a {@code $setOnInsert}-only update (so an existing document is never modified), and pricing
 * validation runs <b>before</b> any write so a bad seed aborts startup with nothing persisted.
 */
class TokenPackSeederTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final PricingValidator pricingValidator = mock(PricingValidator.class);
    private final TokenPackSeeder seeder = new TokenPackSeeder(mongoTemplate, pricingValidator);

    @Test
    void validatesBeforeAnyWriteAndUpsertsEachPack() {
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(TokenPack.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        seeder.run(null);

        InOrder order = inOrder(pricingValidator, mongoTemplate);
        order.verify(pricingValidator).validateActivePacks(any());
        order.verify(mongoTemplate, times(TokenPackSeeder.canonicalPacks().size()))
                .upsert(any(Query.class), any(Update.class), eq(TokenPack.class));
    }

    @Test
    void everyWriteIsSetOnInsertOnly(/* no $set => existing docs never overwritten */) {
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(TokenPack.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        seeder.run(null);

        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(TokenPackSeeder.canonicalPacks().size()))
                .upsert(any(Query.class), updates.capture(), eq(TokenPack.class));

        assertThat(updates.getAllValues()).isNotEmpty();
        for (Update update : updates.getAllValues()) {
            assertThat(update.getUpdateObject().keySet())
                    .containsExactly("$setOnInsert"); // never "$set" — re-runs cannot clobber
        }
    }

    @Test
    void underPricedSeedAbortsBeforeAnyWrite() {
        doThrow(new PricingValidator.SeedPricingException("under-priced"))
                .when(pricingValidator).validateActivePacks(any());

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(PricingValidator.SeedPricingException.class);

        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq(TokenPack.class));
    }

    @Test
    void onlyActivePacksArePassedToValidation() {
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(TokenPack.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        ArgumentCaptor<List<TokenPack>> captor = ArgumentCaptor.captor();
        seeder.run(null);
        verify(pricingValidator).validateActivePacks(captor.capture());

        assertThat(captor.getValue()).allMatch(TokenPack::isActive);
    }
}
