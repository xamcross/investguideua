package com.investguide.metals;

import com.investguide.config.MetalsProperties;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import com.investguide.metals.dto.IngestMetalRequest;
import com.investguide.metals.dto.IngestResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates and persists ingested metal price batches, and resolves the canonical per-gram price used
 * to ground a precious-metals investment option (feature 011).
 *
 * <p><b>Per-record validation.</b> Each record is validated programmatically here: a record failing
 * Bean Validation or date parsing is dropped (counted in {@code rejected}) while the rest proceed
 * (FR-010). <b>Upsert by composite key, never delete:</b> valid records are de-duplicated within the
 * batch by {@code _id} (last occurrence wins) and saved, so repeated runs never create duplicates and
 * combinations absent from a batch keep their last-known row (SC-002, SC-006). A single {@link Instant}
 * is stamped across the whole batch.
 *
 * <p><b>Empty batch is a guarded 400</b> - it can never blank the collection; the scraper additionally
 * never POSTs an empty/failed scrape.
 */
@Service
public class MetalPriceService {

    private static final Logger log = LoggerFactory.getLogger(MetalPriceService.class);

    private final MetalPriceRepository repository;
    private final Validator validator;
    private final MetalsProperties properties;

    public MetalPriceService(MetalPriceRepository repository,
                             Validator validator,
                             MetalsProperties properties) {
        this.repository = repository;
        this.validator = validator;
        this.properties = properties;
    }

    public IngestResult ingest(java.util.List<IngestMetalRequest> batch) {
        if (batch == null || batch.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Metal price batch is empty.");
        }

        Instant fetchedAt = Instant.now();
        Map<String, MetalPrice> byKey = new LinkedHashMap<>(); // last-wins per composite id, ordered
        int rejected = 0;

        for (IngestMetalRequest r : batch) {
            MetalPrice parsed = toMetalPrice(r, fetchedAt);
            if (parsed == null) {
                rejected++;
                continue;
            }
            byKey.put(parsed.getId(), parsed); // duplicate combination in one batch -> last wins
        }

        if (!byKey.isEmpty()) {
            repository.saveAll(byKey.values()); // save() upserts by _id (composite key)
        }
        int accepted = byKey.size();
        log.info("metal_ingest accepted={} rejected={} duplicatesCollapsed={}",
                accepted, rejected, (batch.size() - rejected) - accepted);
        return new IngestResult(accepted, rejected);
    }

    /**
     * The canonical sale rate (kopiykas per gram) for a metal: the smallest weight tier in the
     * configured primary rate group. Empty when no such quote is stored (the caller then drops the
     * option, FR-019).
     */
    public Optional<Long> currentSalePricePerGramMinor(String metal) {
        return repository
                .findFirstByMetalAndRateGroupOrderByWeightGramsAsc(metal, properties.primaryRateGroup())
                .map(MetalPrice::getSaleRateMinor);
    }

    public java.util.List<MetalPrice> findAll() {
        return repository.findAll();
    }

    /** Returns a valid {@link MetalPrice} or {@code null} if the record fails validation/parsing. */
    private MetalPrice toMetalPrice(IngestMetalRequest r, Instant fetchedAt) {
        Set<ConstraintViolation<IngestMetalRequest>> violations = validator.validate(r);
        if (!violations.isEmpty()) {
            return null;
        }
        LocalDate quotationDate = parseDate(r.quotationDate());
        if (quotationDate == null) {
            return null;
        }
        return new MetalPrice(
                r.metal(),
                r.rateGroup().trim(),
                r.weightKey().trim(),
                r.weightGrams(),
                r.currency(),
                r.purchaseRateMinor(),
                r.saleRateMinor(),
                quotationDate,
                fetchedAt);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value); // ISO yyyy-MM-dd
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
