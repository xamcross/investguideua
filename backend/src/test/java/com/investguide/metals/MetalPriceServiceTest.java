package com.investguide.metals;

import com.investguide.config.MetalsProperties;
import com.investguide.common.error.ApiException;
import com.investguide.common.error.ErrorCode;
import com.investguide.metals.dto.IngestMetalRequest;
import com.investguide.metals.dto.IngestResult;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature 011: {@link MetalPriceService} programmatic per-record validation, upsert-by-composite-key
 * (intra-batch last-wins), single {@code fetchedAt} per batch, the empty-batch guard, and the canonical
 * per-gram sale-rate lookup (smallest weight tier of the primary rate group).
 */
class MetalPriceServiceTest {

    private static ValidatorFactory validatorFactory;
    private Validator validator;

    private final MetalPriceRepository repository = mock(MetalPriceRepository.class);
    private final MetalsProperties properties =
            new MetalsProperties(new MetalsProperties.Ingest("secret"), "one");
    private MetalPriceService service;

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
        service = new MetalPriceService(repository, validator, properties);
        when(repository.saveAll(any())).thenAnswer(inv -> {
            List<MetalPrice> out = new ArrayList<>();
            inv.<Iterable<MetalPrice>>getArgument(0).forEach(out::add);
            return out;
        });
    }

    private static IngestMetalRequest valid(String metal, String group, String weightKey,
                                            double weightGrams, long saleMinor) {
        return new IngestMetalRequest(metal, group, weightKey, weightGrams, "UAH", "2026-06-08",
                600000L, saleMinor);
    }

    @SuppressWarnings("unchecked")
    private List<MetalPrice> captureSaved() {
        ArgumentCaptor<Iterable<MetalPrice>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        List<MetalPrice> out = new ArrayList<>();
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
        IngestMetalRequest good = valid("GOLD", "one", "1", 1.0, 888000L);
        IngestMetalRequest badMetal = new IngestMetalRequest("PLATINUM", "one", "1", 1.0, "UAH",
                "2026-06-08", 600000L, 888000L);

        IngestResult result = service.ingest(List.of(good, badMetal));

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.rejected()).isEqualTo(1);
        assertThat(captureSaved()).singleElement()
                .satisfies(m -> assertThat(m.getMetal()).isEqualTo("GOLD"));
    }

    @Test
    void badMetalNegativeRateAndBadDateAreRejectedPerRecord() {
        IngestMetalRequest good = valid("SILVER", "one", "10", 10.0, 28790L);
        IngestMetalRequest negativeRate = new IngestMetalRequest("GOLD", "one", "1", 1.0, "UAH",
                "2026-06-08", 600000L, -5L);
        IngestMetalRequest badDate = new IngestMetalRequest("GOLD", "one", "2", 2.0, "UAH",
                "not-a-date", 600000L, 857000L);
        IngestMetalRequest badCurrency = new IngestMetalRequest("GOLD", "one", "5", 5.0, "USD",
                "2026-06-08", 600000L, 826500L);

        IngestResult result = service.ingest(List.of(good, negativeRate, badDate, badCurrency));

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.rejected()).isEqualTo(3);
    }

    @Test
    void omittedRequiredFieldIsRejectedNotSilentlyDefaulted() {
        IngestMetalRequest missingRate = new IngestMetalRequest("GOLD", "one", "1", 1.0, "UAH",
                "2026-06-08", 600000L, null);
        IngestMetalRequest missingWeight = new IngestMetalRequest("GOLD", "one", "2", null, "UAH",
                "2026-06-08", 600000L, 857000L);

        IngestResult result = service.ingest(List.of(missingRate, missingWeight));

        assertThat(result.accepted()).isZero();
        assertThat(result.rejected()).isEqualTo(2);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void duplicateCompositeKeyWithinBatchKeepsLastOccurrence_andDoesNotFragmentFractionalWeight() {
        IngestMetalRequest first = valid("GOLD", "one", "2.5", 2.5, 848000L);
        IngestMetalRequest second = valid("GOLD", "one", "2.5", 2.5, 999000L); // same combination, newer rate

        IngestResult result = service.ingest(List.of(first, second));

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(captureSaved()).singleElement().satisfies(m -> {
            assertThat(m.getId()).isEqualTo("GOLD:one:2.5"); // verbatim weightKey, no fragmentation
            assertThat(m.getSaleRateMinor()).isEqualTo(999000L);
        });
    }

    @Test
    void singleFetchedAtAppliedAcrossWholeBatch() {
        Instant before = Instant.now();
        service.ingest(List.of(valid("GOLD", "one", "1", 1.0, 1L), valid("SILVER", "one", "10", 10.0, 2L)));
        Instant after = Instant.now();

        List<MetalPrice> saved = captureSaved();
        assertThat(saved).hasSize(2);
        Instant stamp = saved.get(0).getFetchedAt();
        assertThat(stamp).isNotNull();
        assertThat(saved).allSatisfy(m -> assertThat(m.getFetchedAt()).isEqualTo(stamp));
        assertThat(stamp).isBetween(before, after);
    }

    @Test
    void currentSalePricePerGramMinor_returnsSmallestTierSaleRateOfPrimaryGroup() {
        MetalPrice smallest = new MetalPrice("GOLD", "one", "1", 1.0, "UAH",
                678000L, 888000L, LocalDate.parse("2026-06-08"), Instant.now());
        when(repository.findFirstByMetalAndRateGroupOrderByWeightGramsAsc("GOLD", "one"))
                .thenReturn(Optional.of(smallest));

        assertThat(service.currentSalePricePerGramMinor("GOLD")).contains(888000L);
    }

    @Test
    void currentSalePricePerGramMinor_emptyWhenNoStoredPrice() {
        when(repository.findFirstByMetalAndRateGroupOrderByWeightGramsAsc("SILVER", "one"))
                .thenReturn(Optional.empty());

        assertThat(service.currentSalePricePerGramMinor("SILVER")).isEmpty();
    }
}
