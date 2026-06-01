package com.investguide.tokens;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Idempotent token-pack seeder with seed-time pricing validation (SPECIFICATION §6
 * {@code tokenPacks}, §9.1, §14; ticket X7).
 *
 * <p>Runs first ({@code @Order(0)}) so pricing validation aborts startup <b>before</b> any other
 * seeding if a pack is under-priced. The canonical packs are the §9.1 shipping defaults
 * ({@code pack-5/10/25} at 99/169/379 UAH), stored as integer minor units (kopiykas).
 *
 * <p><b>Idempotency / upsert-by-{@code _id}:</b> each pack is written with {@code $setOnInsert}
 * only, so re-running the seeder never duplicates a pack and never overwrites a manually edited
 * field (e.g. an operator toggling {@code active=false} in the DB stays toggled). To intentionally
 * re-price or re-activate a pack, edit the document directly (catalog is DB-managed for the MVP,
 * §1.2/§3) — the seeder will not undo it.
 *
 * <p>If validation throws, the exception propagates out of {@link #run} and Spring Boot aborts
 * startup with the descriptive message (X7 DoD).
 */
@Component
@Order(0)
public class TokenPackSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TokenPackSeeder.class);

    private final MongoTemplate mongoTemplate;
    private final PricingValidator pricingValidator;

    public TokenPackSeeder(MongoTemplate mongoTemplate, PricingValidator pricingValidator) {
        this.mongoTemplate = mongoTemplate;
        this.pricingValidator = pricingValidator;
    }

    /** The §9.1 shipping packs. Prices are kopiykas: 99/169/379 UAH. */
    static List<TokenPack> canonicalPacks() {
        return List.of(
                new TokenPack("pack-5", 5, 9_900L, "UAH", true),
                new TokenPack("pack-10", 10, 16_900L, "UAH", true),
                new TokenPack("pack-25", 25, 37_900L, "UAH", true));
    }

    @Override
    public void run(ApplicationArguments args) {
        List<TokenPack> packs = canonicalPacks();

        // Validate active packs BEFORE any write; an under-priced seed aborts startup.
        List<TokenPack> activePacks = packs.stream().filter(TokenPack::isActive).toList();
        pricingValidator.validateActivePacks(activePacks);

        int inserted = 0;
        for (TokenPack pack : packs) {
            if (upsertByIdInsertOnly(pack)) {
                inserted++;
            }
        }
        log.info("Token-pack seed complete: {} pack(s) defined, {} newly inserted (existing left untouched).",
                packs.size(), inserted);
    }

    /**
     * Insert-only upsert keyed by {@code _id}. Returns {@code true} if a new document was inserted,
     * {@code false} if a pack with this id already existed (left unchanged).
     */
    private boolean upsertByIdInsertOnly(TokenPack pack) {
        Query query = Query.query(Criteria.where("_id").is(pack.getId()));
        Update update = new Update()
                .setOnInsert("tokens", pack.getTokens())
                .setOnInsert("priceMinorUnits", pack.getPriceMinorUnits())
                .setOnInsert("currency", pack.getCurrency())
                .setOnInsert("active", pack.isActive());
        return mongoTemplate.upsert(query, update, TokenPack.class).getUpsertedId() != null;
    }
}
