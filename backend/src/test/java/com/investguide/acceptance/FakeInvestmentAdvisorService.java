package com.investguide.acceptance;

import com.investguide.investment.AdvisorResult;
import com.investguide.investment.AdvisorUnavailableException;
import com.investguide.investment.InvestmentAdvisorService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic fake advisor used by the QA1 acceptance suite (TASKS.md QA1: "with a fake
 * {@code InvestmentAdvisorService} ..."). It replaces the live Anthropic client so the acceptance
 * criteria can be exercised without any network/LLM call, while still flowing through the <em>real</em>
 * {@link com.investguide.investment.InvestmentSearchService} orchestration and the <em>real</em>
 * {@link com.investguide.investment.AdvisorOutputParser} catalog enforcement.
 *
 * <p>The fake is scriptable: each {@link #advise} call pops the next scripted response. A scripted
 * {@link AdvisorResult} is returned as-is (its {@code text} is fed to the real parser); a scripted
 * {@link AdvisorUnavailableException} is thrown to simulate a transport/timeout fault. {@link #calls}
 * records how many times the advisor was invoked, so a test can assert AC #2 ("no LLM call") and the
 * BE-S6 "exactly one corrective retry" contract.
 */
public final class FakeInvestmentAdvisorService implements InvestmentAdvisorService {

    /** Each entry is either an {@link AdvisorResult} (returned) or a {@link RuntimeException} (thrown). */
    private final Deque<Object> script = new ArrayDeque<>();
    private final AtomicInteger calls = new AtomicInteger(0);

    /** Script the next call to return the given raw model text with the given token usage. */
    public FakeInvestmentAdvisorService thenReturn(String text, int inputTokens, int outputTokens) {
        script.addLast(new AdvisorResult(text, inputTokens, outputTokens));
        return this;
    }

    /** Script the next call to throw — simulates a §8.5 transport/timeout failure (not retried). */
    public FakeInvestmentAdvisorService thenThrowUnavailable() {
        script.addLast(new AdvisorUnavailableException("simulated upstream failure"));
        return this;
    }

    /** Number of times {@link #advise} was actually invoked (AC #2: must be 0 on the 402 path). */
    public int calls() {
        return calls.get();
    }

    @Override
    public AdvisorResult advise(String systemPrompt, String userPrompt) {
        calls.incrementAndGet();
        Object next = script.pollFirst();
        if (next == null) {
            throw new AdvisorUnavailableException("fake advisor: no scripted response remaining");
        }
        if (next instanceof RuntimeException ex) {
            throw ex;
        }
        return (AdvisorResult) next;
    }
}
