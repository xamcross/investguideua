package com.investguide.investment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit test for the X6 cost formula on {@link LlmUsage} (ticket X6 DoD #2):
 * {@code costUsd = inputTokens/1e6 * inPrice + outputTokens/1e6 * outPrice}.
 *
 * <p>Pins the computation so a change to the formula or the configured per-million prices is caught,
 * since the per-search cost line drives cost monitoring (and the §9.1 pricing-safety inequality).
 */
class LlmUsageTest {

    @Test
    void costUsd_matchesTheConfiguredPerMillionPrices() {
        // 200 input tokens @ $1/M, 100 output tokens @ $5/M.
        LlmUsage usage = LlmUsage.of(200, 100, 1.0, 5.0);

        assertThat(usage.inputTokens()).isEqualTo(200);
        assertThat(usage.outputTokens()).isEqualTo(100);
        // 200/1e6 * 1.0 + 100/1e6 * 5.0 = 0.0002 + 0.0005 = 0.0007
        assertThat(usage.costUsd()).isCloseTo(0.0007, within(1e-12));
    }

    @Test
    void costUsd_isZeroForNoUsage() {
        LlmUsage usage = LlmUsage.of(0, 0, 1.0, 5.0);
        assertThat(usage.costUsd()).isEqualTo(0.0);
    }

    @Test
    void costUsd_scalesEachSideIndependently() {
        // Only input tokens consumed (e.g. a response that produced no output before failing).
        LlmUsage inputOnly = LlmUsage.of(3000, 0, 1.0, 5.0);
        assertThat(inputOnly.costUsd()).isCloseTo(0.003, within(1e-12));

        // Only output tokens (hypothetical) — verifies the output price is applied, not the input one.
        LlmUsage outputOnly = LlmUsage.of(0, 700, 1.0, 5.0);
        assertThat(outputOnly.costUsd()).isCloseTo(0.0035, within(1e-12));
    }
}
