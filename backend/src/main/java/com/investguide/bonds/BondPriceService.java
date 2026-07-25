package com.investguide.bonds;

import com.investguide.bonds.dto.IngestBondRequest;
import com.investguide.bonds.dto.IngestResult;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 *
 * <p><b>Matured quotes are filtered at READ time.</b> Because ingest never deletes, ISINs that
 * mature and drop out of the source feed linger in the collection with their last quote. Every
 * read path that feeds a consumer (public /latest, advisor prompt, per-ISIN grounding) excludes
 * bonds whose maturity is before "today" in the Kyiv market calendar. The admin listing stays
 * unfiltered on purpose - operators should see the zombies.
 */
@Service
public class BondPriceService {

    private static final Logger log = LoggerFactory.getLogger(BondPriceService.class);

    /** The market's calendar for the "is it matured yet" boundary. */
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private final BondPriceRepository repository;
    private final Validator validator;
    private final Clock clock;

    @Autowired
    public BondPriceService(BondPriceRepository repository, Validator validator) {
        this(repository, validator, Clock.system(KYIV));
    }

    /** Test seam: fixed clocks keep maturity-boundary tests deterministic in any CI timezone. */
    BondPriceService(BondPriceRepository repository, Validator validator, Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.clock = clock;
    }

    /** A quote is current while its maturity is today or later (payout day still counts). */
    private boolean isCurrent(BondPrice bond) {
        return !bond.getMaturity().isBefore(LocalDate.now(clock));
    }

    /**
     * All stored quotes that have not yet matured - the snapshot behind the public
     * {@code GET /api/v1/bond-prices/latest} endpoint (feature 015).
     */
    public java.util.List<BondPrice> listCurrent() {
        return repository.findAll().stream()
                .filter(this::isCurrent)
                .toList();
    }

    /**
     * The latest stored quote for one bond, by ISIN (the {@code _id}). Used to ground a bond investment
     * option server-side (feature 012); empty when the ISIN is not stored, so the option is dropped.
     */
    public java.util.Optional<BondPrice> findByIsin(String isin) {
        if (isin == null || isin.isBlank()) {
            return java.util.Optional.empty();
        }
        return repository.findById(isin.trim()).filter(this::isCurrent);
    }

    /**
     * Stored bonds in the given currency, for listing to the advisor so it can choose a real ISIN
     * (feature 012). Filtered to the exact currency string (so e.g. EUR bonds are excluded from a UAH or
     * USD search). The caller bounds/truncates the list to fit the prompt token budget.
     */
    public java.util.List<BondPrice> listForPrompt(String currency) {
        if (currency == null) {
            return java.util.List.of();
        }
        return repository.findAll().stream()
                .filter(this::isCurrent)
                .filter(b -> currency.equalsIgnoreCase(b.getCurrency()))
                .toList();
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
