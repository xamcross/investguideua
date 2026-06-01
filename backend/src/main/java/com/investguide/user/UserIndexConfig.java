package com.investguide.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Ensures the unique index on {@code users.email} exists, programmatically (ticket BE-A1 DoD:
 * "Create the unique index on email programmatically").
 *
 * <p>Belt-and-braces with the {@code @Indexed(unique=true)} annotation on {@link User}: this
 * runs at startup so the constraint is present even if {@code auto-index-creation} is disabled.
 * {@code ensureIndex} is idempotent.
 */
@Component
public class UserIndexConfig {

    private static final Logger log = LoggerFactory.getLogger(UserIndexConfig.class);

    private final MongoTemplate mongoTemplate;

    public UserIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        // Name this "email" to MATCH Spring Data's auto-index-creation name for the
        // @Indexed(unique=true) annotation on User.email. With auto-index-creation enabled the
        // annotation already creates {email:1} as "email"; an unnamed ensureIndex would request
        // "email_1" on the same key and fail with error 85 (IndexOptionsConflict). Matching the name
        // makes this idempotent.
        mongoTemplate.indexOps(User.class)
                .ensureIndex(new Index().on("email", org.springframework.data.domain.Sort.Direction.ASC)
                        .unique().named("email"));
        log.info("Ensured unique index on users.email");
    }
}
