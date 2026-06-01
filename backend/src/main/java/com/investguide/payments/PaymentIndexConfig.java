package com.investguide.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * Ensures the unique index on {@code payments.orderId} exists, programmatically (ticket BE-P1 DoD:
 * "Unique index on orderId"). The index is what makes {@code orderId} a hard idempotency key — it
 * guarantees a single payment document per order even under a racing duplicate create.
 *
 * <p>Belt-and-braces with {@code @Indexed(unique=true)} on {@link Payment#getOrderId()}: this runs
 * at startup so the constraint is present even if {@code auto-index-creation} is disabled.
 * {@code ensureIndex} is idempotent. Mirrors {@code UserIndexConfig} (BE-A1).
 */
@Component
public class PaymentIndexConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentIndexConfig.class);

    private final MongoTemplate mongoTemplate;

    public PaymentIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        // IMPORTANT: name this index "orderId" to MATCH the name Spring Data's auto-index-creation
        // gives the @Indexed(unique=true) annotation on Payment.orderId. With auto-index-creation
        // enabled (application.yml) the annotation already creates {orderId:1} as "orderId"; an
        // unnamed ensureIndex here would ask Mongo for "orderId_1" on the same key and fail with
        // error 85 (IndexOptionsConflict). Matching the name makes this call idempotent.
        mongoTemplate.indexOps(Payment.class)
                .ensureIndex(new Index().on("orderId", Sort.Direction.ASC).unique().named("orderId"));
        // PARTIAL unique index: providerInvoiceId is null until createCheckout returns (and on any
        // legacy row), so a plain unique index would collide on the second null. Restricting the index
        // to documents where the field exists keeps it unique among real invoice ids only. There is no
        // @Indexed annotation on this field, so this programmatic definition is the only one.
        mongoTemplate.indexOps(Payment.class)
                .ensureIndex(new Index().on("providerInvoiceId", Sort.Direction.ASC).unique()
                        .partial(PartialIndexFilter.of(
                                Criteria.where("providerInvoiceId").exists(true))));
        log.info("Ensured unique index on payments.orderId and partial-unique index on "
                + "payments.providerInvoiceId");
    }
}
