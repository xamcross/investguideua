package com.investguide.bonds;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investguide.common.error.GlobalExceptionHandler;
import com.investguide.config.BondsProperties;
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
 * Feature 009 US3: {@code POST /api/v1/admin/bond-prices} shared-secret ingest. Uses standalone
 * MockMvc so the REAL {@link BondIngestAuth} + {@link BondPriceService} + {@link GlobalExceptionHandler}
 * run (the route is permit-all in the security chain, so the secret helper is the only gate).
 *
 * <p>Covers: valid secret -> 200; wrong/missing secret -> 401; blank server secret -> 401 (fail
 * closed); empty array / non-array / unknown field -> 400; partial batch -> 200 with the
 * accepted/rejected tally and only the valid row stored.
 */
class BondPriceIngestControllerTest {

    private static final String SECRET = "the-shared-secret";
    private static final String HEADER = BondIngestAuth.HEADER;

    private static ValidatorFactory validatorFactory;
    private static ObjectMapper objectMapper;

    private BondPriceRepository repository;

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
        repository = mock(BondPriceRepository.class);
        when(repository.saveAll(any())).thenAnswer(inv -> {
            List<BondPrice> out = new ArrayList<>();
            inv.<Iterable<BondPrice>>getArgument(0).forEach(out::add);
            return out;
        });
    }

    /** Build a standalone MockMvc whose backend has the given configured ingest secret. */
    private MockMvc mvcWithServerSecret(String serverSecret) {
        Validator validator = validatorFactory.getValidator();
        BondIngestAuth auth = new BondIngestAuth(new BondsProperties(new BondsProperties.Ingest(serverSecret)));
        BondPriceService service = new BondPriceService(repository, validator);
        BondPriceIngestController controller = new BondPriceIngestController(auth, service, objectMapper);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static final String VALID_BATCH = """
            [
              {"isin":"UA4000227545","military":true,"currency":"UAH","maturity":"2026-11-18",
               "quotationDate":"2026-06-05","sellPriceMinor":107658,"buyPriceMinor":106900,
               "sellYield":15.25,"buyYield":15.80}
            ]
            """;

    @Test
    void validSecretAndBatchStoresAndReturnsTally() throws Exception {
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/bond-prices")
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
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/bond-prices")
                        .header(HEADER, "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isUnauthorized());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void missingSecretHeaderIsRejected() throws Exception {
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/bond-prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isUnauthorized());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void blankServerSecretRejectsAllIngest() throws Exception {
        // Fail-closed: even presenting an empty secret matching the (blank) config must be rejected.
        mvcWithServerSecret("").perform(post("/api/v1/admin/bond-prices")
                        .header(HEADER, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BATCH))
                .andExpect(status().isUnauthorized());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void emptyArrayIsBadRequest() throws Exception {
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/bond-prices")
                        .header(HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void nonArrayBodyIsBadRequest() throws Exception {
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/bond-prices")
                        .header(HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isin\":\"UA4000227545\"}"))
                .andExpect(status().isBadRequest());

        verify(repository, never()).saveAll(any());
    }

    @Test
    void unknownFieldIsBadRequest() throws Exception {
        String withUnknown = """
                [
                  {"isin":"UA4000227545","military":true,"currency":"UAH","maturity":"2026-11-18",
                   "quotationDate":"2026-06-05","sellPriceMinor":107658,"buyPriceMinor":106900,
                   "sellYield":15.25,"buyYield":15.80,"surpriseField":"x"}
                ]
                """;
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/bond-prices")
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
                  {"isin":"UA4000227545","military":true,"currency":"UAH","maturity":"2026-11-18",
                   "quotationDate":"2026-06-05","sellPriceMinor":107658,"buyPriceMinor":106900,
                   "sellYield":15.25,"buyYield":15.80},
                  {"isin":"  ","military":true,"currency":"UAH","maturity":"2026-11-18",
                   "quotationDate":"2026-06-05","sellPriceMinor":100000,"buyPriceMinor":99000,
                   "sellYield":10.0,"buyYield":10.0}
                ]
                """;
        mvcWithServerSecret(SECRET).perform(post("/api/v1/admin/bond-prices")
                        .header(HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected").value(1));

        verify(repository).saveAll(any());
    }
}
