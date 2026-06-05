package com.investguide.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provider-catalog seeder (SPECIFICATION §6 {@code providers}, §14; ticket X7).
 *
 * <p>Seeds the curated catalog grouped by the thirteen investment instruments available to Ukrainian
 * retail investors ({@link ProviderCategory}) — at least five real, web-verified providers per
 * instrument. Runs after {@link com.investguide.tokens.TokenPackSeeder} ({@code @Order(1)} vs
 * {@code 0}) so seed-time pricing validation aborts startup before any catalog rows are written.
 *
 * <p><b>Per-currency rows.</b> A catalog row is a {@code (provider, instrument, currency-basis)}
 * triple. Figures that depend on the denomination currency — notably bond yields and minimum
 * tickets — differ sharply between hryvnia and hard currency (UAH OVDP ~14-18% vs USD OVDP
 * ~3-4.5%), so a provider that offers an instrument in several currencies is seeded as separate
 * rows ({@code privatbank-mil-uah} vs {@code privatbank-mil-usd}). <b>A row's {@code currencies}
 * are always currency-coherent: either exactly {@code [UAH]} or a subset of {@code [USD, EUR]} —
 * never a mix</b> — so {@code minAmount} and {@code typicalReturnPct} are unambiguous.
 *
 * <p><b>Money units.</b> {@code minAmount} is the minimum ticket in the integer <b>minor units of
 * the row's quote currency</b> ({@code currencies[0]}): a UAH row stores kopiykas, a USD/EUR row
 * stores cents (see {@link Provider}). The {@code amount}/{@code currency} pre-filter (BE-C3) only
 * keeps rows whose {@code currencies} include the requested currency, so the {@code minAmount}
 * comparison is always done within one currency.
 *
 * <p><b>Authoritative reseed.</b> Each canonical row is upserted insert-only ({@code $setOnInsert},
 * so a manually flipped {@code active} flag survives), but {@link #removeNonCanonicalProviders()}
 * first deletes any catalog row whose {@code _id} is not in the current canonical set. This keeps
 * the live catalog exactly equal to {@link #canonicalProviders()} even across slug-scheme changes
 * (it cleans up earlier seed generations); for the MVP the seeder owns the full catalog (§1.2/§3).
 *
 * <p><b>Provisional figures (§14):</b> per-row {@code minAmount}, {@code currencies},
 * {@code typicalReturnPct} and {@code sourceUrl} are indicative, web-verified as of mid-2026, and to
 * be re-confirmed from each provider's current product pages before launch.
 */
@Component
@Order(1)
public class ProviderSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderSeeder.class);

    private final MongoTemplate mongoTemplate;

    public ProviderSeeder(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Compact factory for a seed row. {@code minMajor} is the minimum ticket in whole units of the
     * row's quote currency ({@code currencies.get(0)}); it is stored as integer minor units (×100)
     * per the money-units rule. Every seeded provider is {@code active} with no upper amount limit.
     */
    private static Provider p(String id, String name, ProviderCategory category, String description,
                              long minMajor, List<String> currencies, double retMin, double retMax,
                              RiskLevel riskLevel, String sourceUrl) {
        return new Provider(id, name, category, description, minMajor * 100L, null,
                currencies, new ReturnRange(retMin, retMax), riskLevel, sourceUrl, true);
    }

    /** Currency-basis helpers keep the entries readable. Each list is UAH-only or FX-only. */
    private static final List<String> UAH = List.of("UAH");
    private static final List<String> USD = List.of("USD");
    private static final List<String> EUR = List.of("EUR");
    private static final List<String> USD_EUR = List.of("USD", "EUR");

    /**
     * The curated catalog: at least five verified providers per instrument (SPECIFICATION §6, §14).
     * Ordered by instrument to match the §5.1 transparency screen grouping. Yields and minimum
     * tickets are denominated in each row's quote currency (UAH OVDP ~14-18%; USD OVDP ~3-4.5%).
     */
    static List<Provider> canonicalProviders() {
        return List.of(
                // --- Військові ОВДП (MILITARY_BOND) — гривневі ---
                p("privatbank-mil-uah", "ПриватБанк", ProviderCategory.MILITARY_BOND,
                        "Купівля гривневих військових ОВДП у Приват24 без комісії, від однієї облігації (1000 грн).",
                        1000, UAH, 14.0, 18.0, RiskLevel.LOW, "https://privatbank.ua/ovdp"),
                p("oschadbank-mil-uah", "Ощадбанк", ProviderCategory.MILITARY_BOND,
                        "Гривневі військові ОВДП як первинний дилер Мінфіну, з державною гарантією виплат.",
                        1000, UAH, 14.0, 17.0, RiskLevel.LOW,
                        "https://www.oschadbank.ua/obligacii-vnutrisnoi-derzhavnoi-poziki-ovdp"),
                p("monobank-mil-uah", "monobank", ProviderCategory.MILITARY_BOND,
                        "Гривневі військові ОВДП у розділі Накопичення застосунку monobank без комісії.",
                        1000, UAH, 14.0, 16.5, RiskLevel.LOW, "https://monobank.ua/military-bonds"),
                p("sensebank-mil-uah", "Sense Bank", ProviderCategory.MILITARY_BOND,
                        "Гривневі військові ОВДП у Sense SuperApp або через Дію, від 1000 грн.",
                        1000, UAH, 14.0, 18.0, RiskLevel.LOW, "https://sensebank.ua/vijskovi-obligacii"),
                p("icu-mil-uah", "ICU", ProviderCategory.MILITARY_BOND,
                        "Гривневі військові ОВДП на платформі ICU Trade або через Дію без комісії за угоду.",
                        1000, UAH, 14.0, 17.5, RiskLevel.LOW, "https://icu.ua/investments"),
                // --- Військові ОВДП (MILITARY_BOND) — валютні ---
                p("privatbank-mil-usd", "ПриватБанк", ProviderCategory.MILITARY_BOND,
                        "Доларові військові ОВДП у Приват24: нижча дохідність, ніж у гривневих, але захист від девальвації.",
                        1000, USD, 3.0, 4.5, RiskLevel.LOW, "https://privatbank.ua/ovdp"),
                p("monobank-mil-usd", "monobank", ProviderCategory.MILITARY_BOND,
                        "Доларові військові ОВДП у застосунку monobank, з виплатами у валюті.",
                        1000, USD, 3.0, 4.2, RiskLevel.LOW, "https://monobank.ua/military-bonds"),
                p("sensebank-mil-usd", "Sense Bank", ProviderCategory.MILITARY_BOND,
                        "Доларові військові ОВДП у Sense SuperApp для захисту валютних заощаджень.",
                        1000, USD, 3.0, 4.5, RiskLevel.LOW, "https://sensebank.ua/vijskovi-obligacii"),

                // --- Гривневі/валютні ОВДП (GOV_BOND) — гривневі ---
                p("privatbank-ovdp-uah", "ПриватБанк", ProviderCategory.GOV_BOND,
                        "Звичайні гривневі ОВДП у Приват24 з державною гарантією та податковою пільгою на купон.",
                        1000, UAH, 14.0, 18.0, RiskLevel.LOW, "https://privatbank.ua/ovdp"),
                p("otpbank-ovdp-uah", "OTP Bank", ProviderCategory.GOV_BOND,
                        "Гривневі ОВДП у мобільному застосунку OTP Bank UA з первинного аукціону Мінфіну.",
                        1000, UAH, 14.0, 16.5, RiskLevel.LOW,
                        "https://www.otpbank.com.ua/privateclients/investments/ovdp-app/"),
                p("monobank-ovdp-uah", "monobank", ProviderCategory.GOV_BOND,
                        "Гривневі ОВДП у застосунку monobank без комісії, з фіксованою дохідністю.",
                        1000, UAH, 14.0, 16.2, RiskLevel.LOW, "https://monobank.ua/military-bonds"),
                p("kinto-ovdp-uah", "КІНТО", ProviderCategory.GOV_BOND,
                        "Купівля гривневих ОВДП у Кабінеті інвестора КІНТО без комісії за угоду, від 1000 грн.",
                        1000, UAH, 14.0, 18.5, RiskLevel.LOW, "https://kinto.com/"),
                p("univer-ovdp-uah", "Універ Капітал", ProviderCategory.GOV_BOND,
                        "Гривневі ОВДП у застосунку UNIVER з комісією 1 грн за операцію, від 1000 грн.",
                        1000, UAH, 14.0, 16.6, RiskLevel.LOW, "https://www.univer.ua/univer-derzhavni-obligaciyi"),
                // --- Гривневі/валютні ОВДП (GOV_BOND) — валютні ---
                p("privatbank-ovdp-usd", "ПриватБанк", ProviderCategory.GOV_BOND,
                        "Доларові ОВДП у Приват24: захист від девальвації з дохідністю близько 3-4% річних.",
                        1000, USD, 3.0, 4.5, RiskLevel.LOW, "https://privatbank.ua/ovdp"),
                p("oschadbank-ovdp-usd", "Ощадбанк", ProviderCategory.GOV_BOND,
                        "Доларові ОВДП через державний банк як первинний дилер Мінфіну, з державною гарантією.",
                        1000, USD, 3.0, 4.0, RiskLevel.LOW,
                        "https://www.oschadbank.ua/obligacii-vnutrisnoi-derzhavnoi-poziki-ovdp"),
                p("monobank-ovdp-usd", "monobank", ProviderCategory.GOV_BOND,
                        "Доларові ОВДП у застосунку monobank, із виплатою купона у валюті.",
                        1000, USD, 3.0, 4.2, RiskLevel.LOW, "https://monobank.ua/military-bonds"),

                // --- Валюта готівкою (CASH_CURRENCY) ---
                p("privatbank-cash", "ПриватБанк", ProviderCategory.CASH_CURRENCY,
                        "Купівля доларів і євро готівкою в касі відділення за курсом банку; великі суми замовляють заздалегідь.",
                        50, USD_EUR, 0.0, 3.0, RiskLevel.LOW, "https://privatbank.ua/obmin-valiut"),
                p("oschadbank-cash", "Ощадбанк", ProviderCategory.CASH_CURRENCY,
                        "Купівля готівкових доларів і євро в касі державного банку за касовим курсом.",
                        50, USD_EUR, 0.0, 3.0, RiskLevel.LOW, "https://www.oschadbank.ua/currency-rate"),
                p("pumb-cash", "ПУМБ", ProviderCategory.CASH_CURRENCY,
                        "Купівля готівкових доларів і євро в касі ПУМБ із попереднім бронюванням у застосунку ПУМБ Online.",
                        50, USD_EUR, 0.0, 3.0, RiskLevel.LOW, "https://www.pumb.ua/currency_order"),
                p("raiffeisen-cash", "Райффайзен Банк", ProviderCategory.CASH_CURRENCY,
                        "Купівля та продаж доларів і євро у відділеннях і онлайн через Raiffeisen Online.",
                        50, USD_EUR, 0.0, 3.0, RiskLevel.LOW, "https://raiffeisen.ua/"),
                p("sensebank-cash", "Sense Bank", ProviderCategory.CASH_CURRENCY,
                        "Обмін валюти у відділенні або в застосунку Sense SuperApp відповідно до правил НБУ.",
                        50, USD_EUR, 0.0, 3.0, RiskLevel.LOW, "https://sensebank.ua/"),

                // --- Банківські метали (PRECIOUS_METALS) — гривневі ---
                p("privatbank-metals", "ПриватБанк", ProviderCategory.PRECIOUS_METALS,
                        "Купівля та продаж злитків золота й срібла від 1 грама; з жовтня 2025 операції лише для клієнтів банку.",
                        7000, UAH, 0.0, 10.0, RiskLevel.MODERATE, "https://privatbank.ua/banking-metals"),
                p("ukrgasbank-metals", "Укргазбанк", ProviderCategory.PRECIOUS_METALS,
                        "Купівля штампованих злитків банківських металів найвищих проб від 1 грама за гривню.",
                        7000, UAH, 0.0, 10.0, RiskLevel.MODERATE, "https://www.ukrgasbank.com/private/bank_metals/"),
                p("otpbank-metals", "OTP Bank", ProviderCategory.PRECIOUS_METALS,
                        "Купівля банківського золота за гривню та відповідальне зберігання злитків для приватних клієнтів.",
                        7000, UAH, 0.0, 10.0, RiskLevel.MODERATE,
                        "https://otpbank.com.ua/privateclients/investments/bank-metals/"),
                p("mtbbank-metals", "МТБ БАНК", ProviderCategory.PRECIOUS_METALS,
                        "Купівля та продаж банківських дорогоцінних металів - злитків золота й срібла - для приватних клієнтів.",
                        7000, UAH, 0.0, 10.0, RiskLevel.MODERATE, "https://mtb.ua/BankingMetals"),
                p("kominbank-metals", "КОМІНБАНК", ProviderCategory.PRECIOUS_METALS,
                        "Купівля банківських злитків золота та відкриття рахунків у банківських металах.",
                        7000, UAH, 0.0, 10.0, RiskLevel.MODERATE,
                        "https://cib.com.ua/uk/private/products/operaciji-z-bankivskimi-metalami"),

                // --- Нерухомість (REAL_ESTATE) ---
                p("kan-realty", "KAN Development", ProviderCategory.REAL_ESTATE,
                        "Великий київський забудовник: купівля квартири в новобудові (ціни у доларовому еквіваленті) для здачі в оренду.",
                        50000, USD, 5.0, 10.0, RiskLevel.HIGH, "https://kandevelopment.com/"),
                p("saga-realty", "SAGA Development", ProviderCategory.REAL_ESTATE,
                        "Київський забудовник із десятками ЖК: купівля квартири в новобудові для орендного доходу.",
                        50000, USD, 5.0, 10.0, RiskLevel.HIGH, "https://saga-development.com.ua/"),
                p("intergalbud-realty", "Інтергал-Буд", ProviderCategory.REAL_ESTATE,
                        "Один із лідерів ринку новобудов: квартира в ЖК у Києві чи регіонах для здачі в оренду.",
                        45000, USD, 5.0, 10.0, RiskLevel.HIGH, "https://intergal-bud.com.ua/"),
                p("alliancenovobud-realty", "Alliance Novobud", ProviderCategory.REAL_ESTATE,
                        "Забудовник Києва та Броварів: квартира бізнес-класу в новобудові передмістя під оренду.",
                        45000, USD, 5.0, 10.0, RiskLevel.HIGH, "https://anbud.ua/"),
                p("dimria-realty", "DIM.RIA", ProviderCategory.REAL_ESTATE,
                        "Маркетплейс перевіреної нерухомості: пошук і купівля квартири з подальшою здачею в оренду.",
                        1450000, UAH, 5.0, 10.0, RiskLevel.MODERATE, "https://dom.ria.com/uk/"),
                p("lun-realty", "ЛУН", ProviderCategory.REAL_ESTATE,
                        "Сервіс із найбільшою базою нерухомості: підбір квартири під оренду по всій Україні.",
                        1450000, UAH, 5.0, 10.0, RiskLevel.MODERATE, "https://lun.ua/"),

                // --- Індексні ETF (INDEX_ETF) ---
                p("ibkr-etf", "Interactive Brokers", ProviderCategory.INDEX_ETF,
                        "Глобальний брокер: рахунок у IBKR LLC і купівля індексних ETF на S&P 500 чи світовий ринок, без мінімуму.",
                        100, USD_EUR, 7.0, 12.0, RiskLevel.MODERATE, "https://www.interactivebrokers.com/"),
                p("freedom24-etf", "Freedom24", ProviderCategory.INDEX_ETF,
                        "Брокер з українським корінням: доступ до 1500+ UCITS ETF на біржах Xetra та Euronext без мінімуму.",
                        100, USD_EUR, 7.0, 12.0, RiskLevel.MODERATE, "https://freedom24.com/etf"),
                p("exante-etf", "Exante", ProviderCategory.INDEX_ETF,
                        "Мальтійський брокер: тисячі ETF із 50 бірж, але високий поріг входу (близько 10000 USD).",
                        10000, USD_EUR, 7.0, 12.0, RiskLevel.MODERATE, "https://exante.eu/markets/"),
                p("xtb-etf", "XTB", ProviderCategory.INDEX_ETF,
                        "Регульований брокер з українським інтерфейсом: індексні ETF із нульовою комісією до місячного ліміту.",
                        100, USD_EUR, 7.0, 12.0, RiskLevel.MODERATE, "https://www.xtb.com/int"),
                p("saxo-etf", "Saxo Bank", ProviderCategory.INDEX_ETF,
                        "Данський інвестбанк: доступ до тисяч ETF на глобальних біржах через платформи SaxoInvestor.",
                        500, USD_EUR, 7.0, 12.0, RiskLevel.MODERATE, "https://www.home.saxo/"),

                // --- Окремі іноземні акції (FOREIGN_STOCKS) ---
                p("ibkr-stocks", "Interactive Brokers", ProviderCategory.FOREIGN_STOCKS,
                        "Купівля окремих акцій США та інших ринків зі 135+ бірж через IBKR, з дробовими акціями від кількох доларів.",
                        10, USD_EUR, 8.0, 20.0, RiskLevel.HIGH, "https://www.interactivebrokers.com/"),
                p("freedom24-stocks", "Freedom24", ProviderCategory.FOREIGN_STOCKS,
                        "Доступ до понад мільйона акцій США та Європи з торгівлею на NYSE, Nasdaq і європейських біржах.",
                        10, USD_EUR, 8.0, 20.0, RiskLevel.HIGH, "https://freedom24.com/"),
                p("exante-stocks", "Exante", ProviderCategory.FOREIGN_STOCKS,
                        "Окремі акції США, ЄС та Азії на 50 біржах через єдиний рахунок; високий поріг входу.",
                        10000, USD_EUR, 8.0, 20.0, RiskLevel.HIGH, "https://exante.eu/markets/"),
                p("xtb-stocks", "XTB", ProviderCategory.FOREIGN_STOCKS,
                        "Купівля реальних акцій США та ЄС із нульовою комісією до місячного ліміту обороту.",
                        10, USD_EUR, 8.0, 20.0, RiskLevel.HIGH, "https://www.xtb.com/int"),
                p("etoro-stocks", "eToro", ProviderCategory.FOREIGN_STOCKS,
                        "Простий інтерфейс: іноземні акції без комісії та дробовими частками (доступність для України варто перевірити).",
                        10, USD_EUR, 8.0, 20.0, RiskLevel.HIGH, "https://www.etoro.com/"),

                // --- Криптовалюта (CRYPTO) ---
                p("whitebit-crypto", "WhiteBIT", ProviderCategory.CRYPTO,
                        "Найбільша європейська біржа з українським корінням: поповнення гривнею через картку та P2P, 300+ монет.",
                        400, UAH, 0.0, 60.0, RiskLevel.HIGH, "https://whitebit.com/"),
                p("kuna-crypto", "Kuna", ProviderCategory.CRYPTO,
                        "Українська за походженням біржа, одна з перших, що приймала гривню.",
                        400, UAH, 0.0, 60.0, RiskLevel.HIGH, "https://kuna.io/"),
                p("binance-crypto", "Binance", ProviderCategory.CRYPTO,
                        "Найбільша світова біржа: купівля крипти за гривню через картку та P2P з низькою комісією.",
                        400, UAH, 0.0, 60.0, RiskLevel.HIGH, "https://www.binance.com/"),
                p("kraken-crypto", "Kraken", ProviderCategory.CRYPTO,
                        "Біржа з акцентом на безпеку та Proof of Reserves; купівля топ-активів у доларах від кількох USD.",
                        10, USD, 0.0, 60.0, RiskLevel.HIGH, "https://www.kraken.com/"),
                p("okx-crypto", "OKX", ProviderCategory.CRYPTO,
                        "Глобальна біржа з широким вибором активів і деривативів; торгівля у доларах.",
                        10, USD, 0.0, 60.0, RiskLevel.HIGH, "https://www.okx.com/"),

                // --- Корпоративні облігації (CORPORATE_BOND) — гривневі ---
                p("icu-corp", "ICU", ProviderCategory.CORPORATE_BOND,
                        "Доступ до гривневих корпоративних облігацій українських емітентів через ICU Trade або фонд облігацій.",
                        1000, UAH, 18.0, 25.0, RiskLevel.MODERATE, "https://icu.ua/investments"),
                p("kinto-corp", "КІНТО", ProviderCategory.CORPORATE_BOND,
                        "Купівля гривневих корпоративних облігацій через Кабінет інвестора КІНТО разом з ОВДП.",
                        1000, UAH, 18.0, 25.0, RiskLevel.MODERATE, "https://kinto.com/"),
                p("univer-corp", "Універ Капітал", ProviderCategory.CORPORATE_BOND,
                        "Купівля гривневих корпоративних облігацій у застосунку UNIVER з дохідністю до 25% річних.",
                        1000, UAH, 16.0, 25.0, RiskLevel.MODERATE, "https://www.univer.ua/products"),
                p("novapay-corp", "NovaPay", ProviderCategory.CORPORATE_BOND,
                        "Купівля гривневих корпоративних облігацій NovaPay у застосунку від 1000 грн.",
                        1000, UAH, 12.0, 18.0, RiskLevel.MODERATE, "https://novapay.credit/invest/"),
                p("dragoncapital-corp", "Dragon Capital", ProviderCategory.CORPORATE_BOND,
                        "Брокерський доступ до гривневих корпоративних облігацій українських емітентів.",
                        5000, UAH, 16.0, 24.0, RiskLevel.MODERATE, "https://dragon-capital.com/ua/"),

                // --- Краудлендинг / P2P (CROWDLENDING) ---
                p("inzhur-p2p", "Inzhur", ProviderCategory.CROWDLENDING,
                        "Фонд нерухомості: купівля сертифікатів від 10 грн із щомісячними дивідендами з орендних платежів.",
                        10, UAH, 9.0, 15.0, RiskLevel.HIGH, "https://www.inzhur.reit/"),
                p("smf-p2p", "Сімейні Молочні Ферми", ProviderCategory.CROWDLENDING,
                        "Імпакт-інвестування в агробізнес: внесок частки з виплатами близько 21% річних.",
                        50000, UAH, 15.0, 25.0, RiskLevel.HIGH, "https://invest.smf.org.ua/"),
                p("inventure-p2p", "InVenture", ProviderCategory.CROWDLENDING,
                        "Інвестиційний маркетплейс: вибір проєктів малого бізнесу з поверненням через відсотки чи зростання.",
                        50000, UAH, 15.0, 30.0, RiskLevel.HIGH, "https://inventure.com.ua/"),
                p("fagura-p2p", "Fagura", ProviderCategory.CROWDLENDING,
                        "P2P-краудлендинг: позики бізнесу від 25 EUR з очікуваною дохідністю 10-20% річних.",
                        25, EUR, 10.0, 20.0, RiskLevel.HIGH, "https://fagura.com/"),
                p("mintos-p2p", "Mintos", ProviderCategory.CROWDLENDING,
                        "Європейський P2P-маркетплейс позик і бондів від 50 EUR: портфель кредитів різних оригінаторів.",
                        50, EUR, 10.0, 14.0, RiskLevel.HIGH, "https://www.mintos.com/en/"),

                // --- Недержавні пенсійні фонди (PENSION_FUND) — гривневі ---
                p("otpcapital-npf", "ОТП Пенсія (НПФ)", ProviderCategory.PENSION_FUND,
                        "Найбільший відкритий НПФ: періодичні внески від 100 грн інвестуються для додаткової пенсії.",
                        100, UAH, 10.0, 18.0, RiskLevel.MODERATE, "https://www.otpcapital.com.ua/"),
                p("privatfond-npf", "ПриватФонд (НПФ)", ProviderCategory.PENSION_FUND,
                        "Відкритий пенсійний фонд із внеском від кількох сотень грн: накопичення на додаткову пенсію.",
                        100, UAH, 10.0, 17.0, RiskLevel.MODERATE, "https://privatfond.com.ua/"),
                p("dynasty-npf", "Династія (НПФ, ICU)", ProviderCategory.PENSION_FUND,
                        "Відкритий НПФ під управлінням ICU: регулярні внески інвестуються в облігації для пенсії.",
                        100, UAH, 10.0, 18.0, RiskLevel.MODERATE, "https://dynasty.icu/"),
                p("emeryt-npf", "Емерит-Україна (НПФ)", ProviderCategory.PENSION_FUND,
                        "Один із найбільших відкритих НПФ: пенсійні внески інвестуються для виплати додаткової пенсії.",
                        100, UAH, 10.0, 17.0, RiskLevel.MODERATE, "https://nfp.gov.ua/ua/NPF/3841.html"),
                p("socialstandard-npf", "Соціальний стандарт (НПФ)", ProviderCategory.PENSION_FUND,
                        "Відкритий НПФ для всіх охочих: акумулює добровільні внески задля додаткової пенсії.",
                        100, UAH, 10.0, 17.0, RiskLevel.MODERATE, "https://www.acpo.com.ua/"),

                // --- Накопичувальне страхування життя (LIFE_INSURANCE) — гривневі ---
                p("metlife-life", "MetLife Україна", ProviderCategory.LIFE_INSURANCE,
                        "Накопичувальне страхування життя: регулярні внески дають захист і капітал з інвестдоходом наприкінці строку.",
                        6000, UAH, 4.0, 9.0, RiskLevel.LOW, "https://ukrainelife.com.ua/"),
                p("uniqa-life", "UNIQA Life", ProviderCategory.LIFE_INSURANCE,
                        "Накопичувальне страхування життя: поєднання захисту й накопичення через довгострокові внески.",
                        6000, UAH, 4.0, 8.0, RiskLevel.LOW, "https://uniqa.ua/private/life/zhizn/"),
                p("tas-life", "ТАС Лайф", ProviderCategory.LIFE_INSURANCE,
                        "Програма TAS-LIFE: накопичення плюс захист, строк від 1 до 20 років.",
                        6000, UAH, 4.0, 9.0, RiskLevel.LOW, "https://taslife.com.ua/"),
                p("grawe-life", "GRAWE Україна", ProviderCategory.LIFE_INSURANCE,
                        "Накопичувальні програми страхування життя від австрійського концерну GRAWE з гарантованою виплатою.",
                        6000, UAH, 4.0, 8.0, RiskLevel.LOW, "https://www.grawe.ua/"),
                p("kniazha-life", "Княжа Лайф VIG", ProviderCategory.LIFE_INSURANCE,
                        "Накопичувальне страхування життя та капіталу для дітей під Vienna Insurance Group.",
                        6000, UAH, 4.0, 8.0, RiskLevel.LOW, "https://www.kniazha-life.com.ua/"),

                // --- Малий бізнес / стартапи (BUSINESS_EQUITY) — у валюті ---
                p("iclub-equity", "ICLUB", ProviderCategory.BUSINESS_EQUITY,
                        "Ангельський синдикат: інвестиції від 5000 USD у відібрані стартапи разом з TA Ventures з часткою.",
                        5000, USD, 0.0, 100.0, RiskLevel.HIGH, "https://iclub.vc/"),
                p("taventures-equity", "TA Ventures", ProviderCategory.BUSINESS_EQUITY,
                        "Венчурний фонд ранніх стадій: приватні особи долучаються через синдикат ICLUB від 5000 USD.",
                        5000, USD, 0.0, 100.0, RiskLevel.HIGH, "https://taventures.vc/"),
                p("aventures-equity", "AVentures Capital", ProviderCategory.BUSINESS_EQUITY,
                        "Український венчурний фонд для early- та growth-stage компаній; великий поріг входу.",
                        100000, USD, 0.0, 100.0, RiskLevel.HIGH, "https://aventurescapital.com/"),
                p("smrk-equity", "SMRK VC", ProviderCategory.BUSINESS_EQUITY,
                        "Венчурний фонд інвестицій в IT-стартапи різних стадій; інституційний поріг входу.",
                        100000, USD, 0.0, 100.0, RiskLevel.HIGH, "https://smrk.vc/en/"),
                p("flyerone-equity", "Flyer One Ventures", ProviderCategory.BUSINESS_EQUITY,
                        "Seed-стадійний венчурний фонд групи Genesis: частки у європейських та українських стартапах.",
                        100000, USD, 0.0, 100.0, RiskLevel.HIGH, "https://flyerone.vc/"));
    }

    @Override
    public void run(ApplicationArguments args) {
        removeNonCanonicalProviders();
        List<Provider> providers = canonicalProviders();
        int inserted = 0;
        for (Provider provider : providers) {
            if (upsertByIdInsertOnly(provider)) {
                inserted++;
            }
        }
        log.info("Provider seed complete: {} provider(s) defined, {} newly inserted (existing left untouched).",
                providers.size(), inserted);
    }

    /**
     * Authoritative cleanup: delete every catalog row whose {@code _id} is not in the current
     * canonical set. This removes rows from earlier seed generations (different slug schemes or the
     * pre-13-instrument taxonomy) so the live catalog stays exactly equal to {@link
     * #canonicalProviders()} — no stale duplicates, no rows with retired categories.
     */
    private void removeNonCanonicalProviders() {
        List<String> canonicalIds = canonicalProviders().stream().map(Provider::getId).toList();
        long removed = mongoTemplate.remove(
                Query.query(Criteria.where("_id").nin(canonicalIds)), Provider.class).getDeletedCount();
        if (removed > 0) {
            log.info("Removed {} non-canonical provider(s) before seeding.", removed);
        }
    }

    /**
     * Insert-only upsert keyed by {@code _id}. Returns {@code true} if a new document was inserted,
     * {@code false} if a provider with this id already existed (left unchanged).
     */
    private boolean upsertByIdInsertOnly(Provider provider) {
        Query query = Query.query(Criteria.where("_id").is(provider.getId()));
        Update update = new Update()
                .setOnInsert("name", provider.getName())
                .setOnInsert("category", provider.getCategory())
                .setOnInsert("description", provider.getDescription())
                .setOnInsert("minAmount", provider.getMinAmount())
                .setOnInsert("maxAmount", provider.getMaxAmount())
                .setOnInsert("currencies", provider.getCurrencies())
                .setOnInsert("typicalReturnPct", provider.getTypicalReturnPct())
                .setOnInsert("riskLevel", provider.getRiskLevel())
                .setOnInsert("sourceUrl", provider.getSourceUrl())
                .setOnInsert("active", provider.isActive());
        return mongoTemplate.upsert(query, update, Provider.class).getUpsertedId() != null;
    }
}
