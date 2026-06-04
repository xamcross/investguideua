package com.investguide.config;

import com.investguide.catalog.ProviderCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * MongoDB mapping configuration.
 *
 * <p>Enables auditing so {@code @CreatedDate} / {@code @LastModifiedDate} fields (e.g. on
 * {@link com.investguide.user.User}) are populated automatically on insert/update.
 *
 * <p>Also registers a <b>tolerant {@link ProviderCategory} read converter</b>. The instrument
 * taxonomy was redefined (the legacy {@code BANK_DEPOSIT}/{@code BROKER}/{@code FUND}/{@code OTHER}
 * constants were replaced by the thirteen instrument types). Historical {@code searchRequests}
 * documents embed {@link com.investguide.investment.InvestmentOption}s whose {@code category} was
 * persisted under the old names. Spring Data's default enum mapping calls {@code Enum.valueOf} on
 * read and throws for a retired constant, which fails the entire owner history query with a 500.
 * The reading converter maps any unknown stored value to {@code null} so old searches still load;
 * the writing converter keeps the on-disk format unchanged ({@code enum.name()}).
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new StringToProviderCategoryConverter(),
                new ProviderCategoryToStringConverter()));
    }

    /**
     * Reads a stored category string into a {@link ProviderCategory}, tolerating values from the
     * pre-13-instrument taxonomy by returning {@code null} rather than throwing.
     */
    @ReadingConverter
    static class StringToProviderCategoryConverter implements Converter<String, ProviderCategory> {

        private static final Logger log = LoggerFactory.getLogger(StringToProviderCategoryConverter.class);

        @Override
        @Nullable
        public ProviderCategory convert(String source) {
            try {
                return ProviderCategory.valueOf(source);
            } catch (IllegalArgumentException ex) {
                log.debug("Unknown stored ProviderCategory '{}' mapped to null (legacy taxonomy).", source);
                return null;
            }
        }
    }

    /** Persists a {@link ProviderCategory} as its constant name — the unchanged storage format. */
    @WritingConverter
    static class ProviderCategoryToStringConverter implements Converter<ProviderCategory, String> {

        @Override
        public String convert(ProviderCategory source) {
            return source.name();
        }
    }
}
