package com.investguide.config;

import com.investguide.catalog.ProviderCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tolerant {@link ProviderCategory} Mongo converters: a retired/legacy stored category (from the
 * pre-13-instrument taxonomy persisted in historical {@code searchRequests}) must read back as
 * {@code null} instead of throwing — otherwise the owner history query fails with a 500. The write
 * side keeps the unchanged {@code enum.name()} storage format.
 */
class MongoConfigTest {

    private final MongoConfig.StringToProviderCategoryConverter read =
            new MongoConfig.StringToProviderCategoryConverter();
    private final MongoConfig.ProviderCategoryToStringConverter write =
            new MongoConfig.ProviderCategoryToStringConverter();

    @Test
    void readsCurrentInstrumentNames() {
        assertThat(read.convert("GOV_BOND")).isEqualTo(ProviderCategory.GOV_BOND);
        assertThat(read.convert("CRYPTO")).isEqualTo(ProviderCategory.CRYPTO);
        assertThat(read.convert("BUSINESS_EQUITY")).isEqualTo(ProviderCategory.BUSINESS_EQUITY);
    }

    @Test
    void mapsRetiredLegacyCategoriesToNullInsteadOfThrowing() {
        // These constants existed before the 13-instrument migration and may still be on disk.
        assertThat(read.convert("BANK_DEPOSIT")).isNull();
        assertThat(read.convert("BROKER")).isNull();
        assertThat(read.convert("FUND")).isNull();
        assertThat(read.convert("OTHER")).isNull();
        assertThat(read.convert("definitely-not-a-category")).isNull();
    }

    @Test
    void writesEnumConstantName() {
        assertThat(write.convert(ProviderCategory.GOV_BOND)).isEqualTo("GOV_BOND");
        assertThat(write.convert(ProviderCategory.LIFE_INSURANCE)).isEqualTo("LIFE_INSURANCE");
    }
}
