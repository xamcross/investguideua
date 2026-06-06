package com.investguide.bonds;

import com.investguide.bonds.dto.IngestBondRequest;
import com.investguide.bonds.dto.IngestResult;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
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
import java.util.Set;

/**
 * Validates and persists ingested bond price batches (feature 009).
 *
 * <p><b>Per-record validation.</b> Spring's {@code @Valid} does not cascade to elements of a
 * {@code List} body, and a declarative failure would 400 the whole batch. So each record is
 * validated <i>programmatically</i> here: a record failing Bean Validation or date parsing is
 * dropped (counted in {@code rejected}) while the rest proceed (FR-010).
 *
 * <p><b>Upsert by ISIN, never delete.</b> Valid records are de-duplicated within the batch by ISIN
 * (last occurrence wins) and saved by {@code _id}, so repeated runs never create duplicates and
 * instruments absent from a batch keep their last-known row (SC-002, SC-006). A single
 * {@link Instant} is stamped across the whole batch so it reads as one coherent "as of" time.
 *
 * <p><b>Empty batch is a guarded 400.</b> A null/empty batch is rejected (it can never blank the
 * collection); the scraper additionally never POSTs an empty/failed scrape.
 */
@Service
public class BondPriceService {

    private static final Logger log = LoggerFactory.getLogger(BondPriceService.class);

    private final BondPriceRepository repository;
    private final Validator validator;

    public BondPriceService(BondPriceRepository repository, Validator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public IngestResult ingest(java.util.List<IngestBondRequest> batch) {
        if (batch == null || batch.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Bond price batch is empty.");
        }

        Instant fetchedAt = Instant.now();
        Map<String, BondPrice> byIsin = new LinkedHashMap<>(); // last-wins per ISIN, insertion-ordered
        int rejected = 0;

        for (IngestBondRequest r : batch) {
            BondPrice parsed = toBondPrice(r, fetchedAt);
            if (parsed == null) {
                rejected++;
                continue;
            }
            byIsin.put(parsed.getIsin(), parsed); // duplicate ISIN in one batch -> last wins
        }

        if (!byIsin.isEmpty()) {
            repository.saveAll(byIsin.values()); // save() upserts by _id (ISIN)
        }
        int accepted = byIsin.size();
        log.info("bond_ingest accepted={} rejected={} duplicatesCollapsed={}",
                accepted, rejected, (batch.size() - rejected) - accepted);
        return new IngestResult(accepted, rejected);
    }

    /** Returns a valid {@link BondPrice} or {@code null} if the record fails validation/parsing. */
    private BondPrice toBondPrice(IngestBondRequest r, Instant fetchedAt) {
        Set<ConstraintViolation<IngestBondRequest>> violations = validator.validate(r);
        if (!violations.isEmpty()) {
            return null;
        }
        LocalDate maturity = parseDate(r.maturity());
        LocalDate quotationDate = parseDate(r.quotationDate());
        if (maturity == null || quotationDate == null) {
            return null;
        }
        return new BondPrice(
                r.isin().trim(),
                r.military(),
                r.currency(),
                maturity,
                quotationDate,
                r.sellPriceMinor(),
                r.buyPriceMinor(),
                r.sellYield(),
                r.buyYield(),
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
