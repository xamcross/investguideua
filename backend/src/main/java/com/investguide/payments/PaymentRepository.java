package com.investguide.payments;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link Payment} (ticket BE-P1).
 *
 * <ul>
 *   <li>{@link #findByOrderId(String)} — callback matching by the unique idempotency key (BE-P4).</li>
 *   <li>{@link #findFirstByUserIdAndPackIdAndStatus} — backs the §9.2 idempotent create: reuse an
 *       existing {@code pending} order for the same {@code (userId, packId)} instead of duplicating.</li>
 *   <li>{@link #findByIdAndUserId} — owner-scoped status read (BE-P5); a non-owner gets an empty
 *       result and therefore a {@code 404} (existence is not revealed).</li>
 * </ul>
 */
public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByOrderId(String orderId);

    /** Fallback callback match when a webhook joins by the gateway invoice id (BE-P4). */
    Optional<Payment> findByProviderInvoiceId(String providerInvoiceId);

    Optional<Payment> findFirstByUserIdAndPackIdAndStatus(String userId, String packId, String status);

    Optional<Payment> findByIdAndUserId(String id, String userId);
}
