package com.investguide.metals;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.common.error.GlobalExceptionHandler;
import com.investguide.config.MetalsProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 011 US3: {@code POST /api/v1/admin/metal-prices} shared-secret ingest. Uses standalone
 * MockMvc so the REAL {@link MetalIngestAuth} + {@link MetalPriceService} + {@link GlobalExceptionHandler}
 * run (the route is permit-all in the security chain, so the secret helper is the only gate).
 *
 * <p>Covers: valid secret -> 200; wrong/missing secret -> 401; blank server secret -> 401 (fail
 * closed); empty array / non-array / unknown field -> 400; partial batch -> 200 with the
 * accepted/rejected tally and only the valid row stored.
 */
class MetalPriceIngestControllerTest {

    private static final String SECRET = "the-metal-secret";
    private static final String HEADER = MetalIngestAuth.HEADER;

    private static ValidatorFactory validatorFactory;
    private static ObjectMapper objectMapper;

    private MetalPriceRepository repository;

    @BeforeAll
    static void initShared() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    @AfterAll
    static void closeFactory() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        repository = mock(MetalPriceRepository.class);
        when(repository.saveAll(any())).thenAnswer(inv -> {
            List<MetalPrice> out = new ArrayList<>();
            inv.<Iterable<MetalPrice>>getArgument(0).forEach(out::add);
            return out;
        });
    }

    /** Build a standalone MockMvc whose backend has the given configured ingest secret. */
    private MockMvc mvcWithServerSecret(String serverSecret) {
        Validator validator = validatorFactory.getValidator();
        MetalsProperties props = new MetalsProperties(new MetalsProperties.Ingest(serverSecret), "one");
        MetalIngestAuth auth = new MetalIngestAuth(props);
        MetalPriceService service = new MetalPriceService(repository, validator, props);
        MetalPriceIngestController controller = new MetalPriceIngestController(auth, service, objectMapper);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static final String VALID_BATCH = """
            [
              {"metal":"GOLD","rateGroup":"one","weightKey":"1","weightGrams":1,"currency":"UAH",
               "quotationDate":"2026-06-08","purchaseRateMinor":678000,"saleRateMinor":888000}
            ]
            """;

    @Test
    void validSecretAndBatchStoresAndReturnsTally() throws Exception {
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/metal-prices")
                        .header(HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected").value(0));

        verify(repository).saveAll(any());
    }

    @Test
    void wrongSecretIsRejectedAndNothingStored() throws Exception {
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/metal-prices")
                        .header(HEADER, "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isUnauthorized());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void missingSecretHeaderIsRejected() throws Exception {
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/metal-prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isUnauthorized());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void blankServerSecretRejectsAllIngest() throws Exception {
        // Fail-closed: even presenting an empty secret matching the (blank) config must be rejected.
        mvcWithServerSecret("").perform(post("/api/v1/admin/metal-prices")
                        .header(HEADER, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isUnauthorized());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void emptyArrayIsBadRequest() throws Exception {
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/metal-prices")
                        .header(HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void nonArrayBodyIsBadRequest() throws Exception {
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/metal-prices")
                        .header(HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metal\":\"GOLD\"}"))
                .andExpect(status().isBadRequest());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void unknownFieldIsBadRequest() throws Exception {
        String withUnknown = """
                [
                  {"metal":"GOLD","rateGroup":"one","weightKey":"1","weightGrams":1,"currency":"UAH",
                   "quotationDate":"2026-06-08","purchaseRateMinor":678000,"saleRateMinor":888000,
                   "surpriseField":"x"}
                ]
                """;
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/metal-prices")
                        .header(HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withUnknown))
                .andExpect(status().isBadRequest());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void partialBatchReturnsTallyAndStoresOnlyValid() throws Exception {
        String partial = """
                [
                  {"metal":"GOLD","rateGroup":"one","weightKey":"1","weightGrams":1,"currency":"UAH",
                   "quotationDate":"2026-06-08","purchaseRateMinor":678000,"saleRateMinor":888000},
                  {"metal":"PLATINUM","rateGroup":"one","weightKey":"1","weightGrams":1,"currency":"UAH",
                   "quotationDate":"2026-06-08","purchaseRateMinor":678000,"saleRateMinor":888000}
                ]
                """;
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/metal-prices")
                        .header(HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected").value(1));

        verify(repository).saveAll(any());
    }
}
