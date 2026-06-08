package com.investguide.bonds;

import com.investguide.bonds.dto.IngestBondRequest;
import com.investguide.bonds.dto.IngestResult;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature 009 US3: {@link BondPriceService} programmatic per-record validation, upsert-by-ISIN
 * (intra-batch last-wins), single {@code fetchedAt} per batch, and the empty-batch guard.
 */
class BondPriceServiceTest {

    private static ValidatorFactory validatorFactory;
    private Validator validator;

    private final BondPriceRepository repository = mock(BondPriceRepository.class);
    private BondPriceService service;

    @BeforeAll
    static void initFactory() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeFactory() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        validator = validatorFactory.getValidator();
        service = new BondPriceService(repository, validator);
        when(repository.saveAll(any())).thenAnswer(inv -> {
            List<BondPrice> out = new ArrayList<>();
            inv.<Iterable<BondPrice>>getArgument(0).forEach(out::add);
            return out;
        });
    }

    private static IngestBondRequest valid(String isin, long sellMinor) {
        return new IngestBondRequest(isin, true, "UAH", "2026-11-18", "2026-06-05",
                sellMinor, 106900L, 15.25, 15.80);
    }

    @SuppressWarnings("unchecked")
    private List<BondPrice> captureSaved() {
        ArgumentCaptor<Iterable<BondPrice>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        List<BondPrice> out = new ArrayList<>();
        captor.getValue().forEach(out::add);
        return out;
    }

    @Test
    void emptyBatchIsRejectedAndNothingStored() {
        assertThatThrownBy(() -> service.ingest(List.of()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void nullBatchIsRejected() {
        assertThatThrownBy(() -> service.ingest(null)).isInstanceOf(ApiException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void partialBatchStoresOnlyValidRecords() {
        IngestBondRequest good = valid("UA4000227545", 107658L);
        IngestBondRequest missingIsin = new IngestBondRequest("  ", true, "UAH",
                "2026-11-18", "2026-06-05", 100000L, 99000L, 10.0, 10.0);

        IngestResult result = service.ingest(List.of(good, missingIsin));

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.rejected()).isEqualTo(1);
        assertThat(captureSaved()).singleElement()
                .satisfies(b -> assertThat(b.getIsin()).isEqualTo("UA4000227545"));
    }

    @Test
    void badCurrencyAndNegativePriceAndBadDateAreRejectedPerRecord() {
        IngestBondRequest good = valid("UA4000227545", 107658L);
        IngestBondRequest badCurrency = new IngestBondRequest("UA0000000001", true, "GBP",
                "2026-11-18", "2026-06-05", 100000L, 99000L, 10.0, 10.0);
        IngestBondRequest negativePrice = new IngestBondRequest("UA0000000002", true, "UAH",
                "2026-11-18", "2026-06-05", -5L, 99000L, 10.0, 10.0);
        IngestBondRequest badDate = new IngestBondRequest("UA0000000003", true, "UAH",
                "not-a-date", "2026-06-05", 100000L, 99000L, 10.0, 10.0);

        IngestResult result = service.ingest(List.of(good, badCurrency, negativePrice, badDate));

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.rejected()).isEqualTo(3);
    }

    @Test
    void omittedRequiredFieldIsRejectedNotSilentlyDefaulted() {
        // military/price null (boxed) must reject, never store as false/0.
        IngestBondRequest missingMilitary = new IngestBondRequest("UA0000000004", null, "UAH",
                "2026-11-18", "2026-06-05", 100000L, 99000L, 10.0, 10.0);
        IngestBondRequest missingPrice = new IngestBondRequest("UA0000000005", true, "UAH",
                "2026-11-18", "2026-06-05", null, 99000L, 10.0, 10.0);

        IngestResult result = service.ingest(List.of(missingMilitary, missingPrice));

        assertThat(result.accepted()).isZero();
        assertThat(result.rejected()).isEqualTo(2);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void duplicateIsinWithinBatchKeepsLastOccurrence() {
        IngestBondRequest first = valid("UA4000227545", 100000L);
        IngestBondRequest second = valid("UA4000227545", 107658L); // same ISIN, newer price

        IngestResult result = service.ingest(List.of(first, second));

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(captureSaved()).singleElement()
                .satisfies(b -> assertThat(b.getSellPriceMinor()).isEqualTo(107658L));
    }

    @Test
    void singleFetchedAtAppliedAcrossWholeBatch() {
        Instant before = Instant.now();
        service.ingest(List.of(valid("UA0000000010", 1L), valid("UA0000000011", 2L)));
        Instant after = Instant.now();

        List<BondPrice> saved = captureSaved();
        assertThat(saved).hasSize(2);
        Instant stamp = saved.get(0).getFetchedAt();
        assertThat(stamp).isNotNull();
        assertThat(saved).allSatisfy(b -> assertThat(b.getFetchedAt()).isEqualTo(stamp));
        assertThat(stamp).isBetween(before, after);
    }

    // ---- feature 012: read access used to ground bond investment options --------------------

    private static BondPrice bond(String isin, String currency, long sellMinor, double sellYield) {
        return new BondPrice(isin, true, currency,
                LocalDate.of(2026, 11, 18), LocalDate.of(2026, 6, 8),
                sellMinor, sellMinor - 700L, sellYield, sellYield + 0.5, Instant.now());
    }

    @Test
    void findByIsin_returnsStoredRecordOrEmpty() {
        when(repository.findById("UA4000227545"))
                .thenReturn(java.util.Optional.of(bond("UA4000227545", "UAH", 107658L, 15.25)));
        when(repository.findById("UA0000000000")).thenReturn(java.util.Optional.empty());

        assertThat(service.findByIsin("UA4000227545")).isPresent()
                .get().satisfies(b -> assertThat(b.getSellPriceMinor()).isEqualTo(107658L));
        assertThat(service.findByIsin("UA0000000000")).isEmpty();
        assertThat(service.findByIsin(null)).isEmpty();
        assertThat(service.findByIsin("  ")).isEmpty();
    }

    @Test
    void listForPrompt_returnsOnlyRequestedCurrency_excludingOthers() {
        when(repository.findAll()).thenReturn(List.of(
                bond("UA4000227545", "UAH", 107658L, 15.25),
                bond("UA4000226893", "UAH", 101200L, 16.10),
                bond("XS0000000001", "USD", 99000L, 5.0),
                bond("XS0000000002", "EUR", 98000L, 4.0)));

        List<BondPrice> uah = service.listForPrompt("UAH");

        assertThat(uah).extracting(BondPrice::getIsin)
                .containsExactly("UA4000227545", "UA4000226893"); // USD + EUR excluded
        assertThat(service.listForPrompt("USD")).extracting(BondPrice::getIsin)
                .containsExactly("XS0000000001");
        assertThat(service.listForPrompt(null)).isEmpty();
    }
}
