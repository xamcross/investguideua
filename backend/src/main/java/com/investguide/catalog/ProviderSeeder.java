package com.investguide.catalog;

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
 * Idempotent provider-catalog seeder (SPECIFICATION §6 {@code providers}, §14; ticket X7).
 *
 * <p>Seeds the four reconciled top-bank providers from §14. Runs after {@link
 * com.investguide.tokens.TokenPackSeeder} ({@code @Order(1)} vs {@code 0}) so seed-time pricing
 * validation aborts startup before any catalog rows are written if a pack is mis-priced.
 *
 * <p><b>Idempotency / upsert-by-{@code _id}:</b> like the pack seeder, each provider is written
 * with {@code $setOnInsert} only — re-running never duplicates a provider and never overwrites a
 * manually edited field (notably the {@code active} flag). Operators manage the catalog in the DB
 * for the MVP (§1.2/§3); the seeder only fills in missing rows.
 *
 * <p><b>Seed values are provisional (§14):</b> per-bank {@code minAmount}, {@code currencies},
 * {@code typicalReturnPct} and official {@code sourceUrl} are "to be finalized from each bank's
 * current product pages before launch". {@code minAmount} is a coarse UAH-equivalent threshold in
 * kopiykas (see {@link Provider}); all four banks accept retail deposits/bonds from ~1,000 UAH, so
 * {@code minAmount = 100_000}. The {@code amount}/{@code currency} pre-filter (BE-C3) only narrows
 * the LLM's option set; the model still grounds its picks in this catalog (§8.3).
 */
@Component
@Order(1)
public class ProviderSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderSeeder.class);

    /** ~1,000 UAH expressed in kopiykas — the common retail entry point for all four banks (§14). */
    private static final long MIN_AMOUNT_1000_UAH = 100_000L;

    private final MongoTemplate mongoTemplate;

    public ProviderSeeder(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** The §14 reconciled top-four banks. Values are provisional pending launch finalisation. */
    static List<Provider> canonicalProviders() {
        return List.of(
                new Provider(
                        "privatbank",
                        "ПриватБанк",
                        ProviderCategory.BANK_DEPOSIT,
                        "Найбільший банк України. Купівля/продаж ОВДП у Приват24 (від ~1000 грн; "
                                + "UAH/USD/EUR), без комісії онлайн; строкові депозити.",
                        MIN_AMOUNT_1000_UAH,
                        null,
                        List.of("UAH", "USD"),
                        new ReturnRange(13.0, 16.5),
                        RiskLevel.LOW,
                        "https://privatbank.ua/ovdp",
                        true),
                new Provider(
                        "oschadbank",
                        "Ощадбанк",
                        ProviderCategory.GOV_BOND,
                        "Другий за величиною державний банк. Акредитований первинний дилер ОВДП "
                                + "(100% державна гарантія); депозити від 1000 грн.",
                        MIN_AMOUNT_1000_UAH,
                        null,
                        List.of("UAH", "USD"),
                        new ReturnRange(14.0, 17.0),
                        RiskLevel.LOW,
                        "https://www.oschadbank.ua",
                        true),
                new Provider(
                        "otpbank",
                        "OTP Bank",
                        ProviderCategory.BROKER,
                        "Первинний дилер. Повний самообслуговуючий продаж/купівля ОВДП у застосунку "
                                + "плюс пайові фонди OTP Capital та депозити — найширший асортимент.",
                        MIN_AMOUNT_1000_UAH,
                        null,
                        List.of("UAH", "USD"),
                        new ReturnRange(13.0, 18.0),
                        RiskLevel.MODERATE,
                        "https://www.otpbank.com.ua",
                        true),
                new Provider(
                        "monobank",
                        "monobank",
                        ProviderCategory.BANK_DEPOSIT,
                        "Провідний цифровий банк за охопленням. Строкові депозити (від 1000 грн / "
                                + "$100 / EUR100) та ОВДП-забезпечені державні й військові облігації у застосунку.",
                        MIN_AMOUNT_1000_UAH,
                        null,
                        List.of("UAH", "USD"),
                        new ReturnRange(12.0, 15.0),
                        RiskLevel.LOW,
                        "https://www.monobank.ua",
                        true));
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Provider> providers = canonicalProviders();
        int inserted = 0;
        for (Provider provider : providers) {
            if (upsertByIdInsertOnly(provider)) {
                inserted++;
            }
        }
        log.info("Provider seed complete: {} provider(s) defined, {} newly inserted (existing left untouched).",
                providers.size(), inserted);
    }

    /**
     * Insert-only upsert keyed by {@code _id}. Returns {@code true} if a new document was inserted,
     * {@code false} if a provider with this id already existed (left unchanged).
     */
    private boolean upsertByIdInsertOnly(Provider provider) {
        Query query = Query.query(Criteria.where("_id").is(provider.getId()));
        Update update = new Update()
                .setOnInsert("name", provider.getName())
                .setOnInsert("category", provider.getCategory())
                .setOnInsert("description", provider.getDescription())
                .setOnInsert("minAmount", provider.getMinAmount())
                .setOnInsert("maxAmount", provider.getMaxAmount())
                .setOnInsert("currencies", provider.getCurrencies())
                .setOnInsert("typicalReturnPct", provider.getTypicalReturnPct())
                .setOnInsert("riskLevel", provider.getRiskLevel())
                .setOnInsert("sourceUrl", provider.getSourceUrl())
                .setOnInsert("active", provider.isActive());
        return mongoTemplate.upsert(query, update, Provider.class).getUpsertedId() != null;
    }
}
