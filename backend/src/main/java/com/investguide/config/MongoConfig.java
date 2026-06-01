package com.investguide.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Enables MongoDB auditing so {@code @CreatedDate} / {@code @LastModifiedDate} fields (e.g. on
 * {@link com.investguide.user.User}) are populated automatically on insert/update.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
