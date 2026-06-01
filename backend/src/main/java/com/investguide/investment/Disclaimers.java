package com.investguide.investment;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Server-controlled financial disclaimers (SPECIFICATION §1.1, §8.6, §10, §14; ticket BE-S7).
 *
 * <p>The disclaimers are <b>always</b> appended server-side, independent of the model output, so the
 * mandatory financial disclaimer (§1.1, AC #5) can never be omitted or altered by the LLM. When any
 * returned option is denominated in a currency different from the user's requested deposit currency
 * (e.g. a USD option for a UAH request), an <b>additional currency-risk disclaimer</b> is added (§10,
 * §14). Both strings are fixed, server-owned text — never sourced from the model.
 */
@Component
public class Disclaimers {

    /** §1.1 mandatory disclaimer (Ukrainian) — present on every result set. */
    public static final String STANDARD =
            "Це інформаційний матеріал, а не індивідуальна інвестиційна рекомендація. "
                    + "Дохідність не гарантована. Рішення ухвалюйте самостійно або з ліцензованим "
                    + "радником.";

    /** Additional disclaimer when an option's currency differs from the requested currency. */
    public static final String CURRENCY_RISK =
            "Деякі варіанти номіновані в іншій валюті, ніж сума вашого запиту. Зміна валютного курсу "
                    + "може суттєво вплинути на фактичну дохідність у вашій валюті.";

    /** The mandatory disclaimer, always returned. */
    public String standard() {
        return STANDARD;
    }

    /**
     * The currency-risk disclaimer when applicable, else {@code null} (the field is omitted from JSON).
     *
     * @param options         the returned options (currency-tagged server-side)
     * @param requestCurrency the user's requested deposit currency
     */
    public String currencyRiskOrNull(List<InvestmentOption> options, SearchCurrency requestCurrency) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        boolean differs = options.stream()
                .anyMatch(o -> o.currency() != null && o.currency() != requestCurrency);
        return differs ? CURRENCY_RISK : null;
    }
}
