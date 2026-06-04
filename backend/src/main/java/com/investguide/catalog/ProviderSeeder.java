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

import java.util.Arrays;
import java.util.List;

/**
 * Idempotent provider-catalog seeder (SPECIFICATION §6 {@code providers}, §14; ticket X7).
 *
 * <p>Seeds the curated catalog grouped by the thirteen investment instruments available to
 * Ukrainian retail investors ({@link ProviderCategory}) — at least five real, web-verified
 * providers per instrument. Runs after {@link com.investguide.tokens.TokenPackSeeder}
 * ({@code @Order(1)} vs {@code 0}) so seed-time pricing validation aborts startup before any
 * catalog rows are written if a pack is mis-priced.
 *
 * <p><b>Keying:</b> a catalog row is a {@code (provider, instrument)} pairing — the same bank can
 * legitimately sell several instruments (e.g. ПриватБанк offers war bonds, ordinary OVDP, cash FX
 * and bullion), so each pairing has its own stable slug ({@code <provider>-<instrument>}) and is a
 * distinct recommendation identity (§8.3, BE-S6).
 *
 * <p><b>Idempotency / upsert-by-{@code _id}:</b> like the pack seeder, each provider is written
 * with {@code $setOnInsert} only — re-running never duplicates a provider and never overwrites a
 * manually edited field (notably the {@code active} flag). Operators manage the catalog in the DB
 * for the MVP (§1.2/§3); the seeder only fills in missing rows.
 *
 * <p><b>Provisional figures (§14):</b> per-provider {@code minAmount}, {@code currencies},
 * {@code typicalReturnPct} and {@code sourceUrl} are indicative, web-verified as of mid-2026, and
 * to be re-confirmed from each provider's current product pages before launch. {@code minAmount} is
 * a coarse UAH-equivalent threshold in kopiykas (see {@link Provider}); the {@code amount}/
 * {@code currency} pre-filter (BE-C3) only narrows the LLM's option set.
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
     * Compact factory for a seed row. {@code minUah} is the coarse UAH-equivalent entry threshold in
     * whole hryvnia; it is stored as integer minor units (kopiykas) per the money-units rule. Every
     * seeded provider is {@code active} with no upper amount limit.
     */
    private static Provider p(String id, String name, ProviderCategory category, String description,
                              long minUah, List<String> currencies, double retMin, double retMax,
                              RiskLevel riskLevel, String sourceUrl) {
        return new Provider(id, name, category, description, minUah * 100L, null,
                currencies, new ReturnRange(retMin, retMax), riskLevel, sourceUrl, true);
    }

    /** UAH/USD/EUR helpers keep the (long) entries readable. */
    private static final List<String> UAH = List.of("UAH");
    private static final List<String> UAH_USD = List.of("UAH", "USD");
    private static final List<String> UAH_USD_EUR = List.of("UAH", "USD", "EUR");
    private static final List<String> USD = List.of("USD");
    private static final List<String> EUR = List.of("EUR");
    private static final List<String> USD_EUR = List.of("USD", "EUR");
    private static final List<String> UAH_EUR = List.of("UAH", "EUR");

    /**
     * The curated catalog: at least five verified providers per instrument (SPECIFICATION §6, §14).
     * Ordered by instrument to match the §5.1 transparency screen grouping.
     */
    static List<Provider> canonicalProviders() {
        return List.of(
                // --- Військові ОВДП (MILITARY_BOND) ---
                p("privatbank-mil", "ПриватБанк", ProviderCategory.MILITARY_BOND,
                        "Купівля військових ОВДП у Приват24 без комісії, у гривні чи валюті, від однієї облігації.",
                        1000, UAH_USD_EUR, 14.0, 17.5, RiskLevel.LOW, "https://privatbank.ua/ovdp"),
                p("oschadbank-mil", "Ощадбанк", ProviderCategory.MILITARY_BOND,
                        "Військові ОВДП через відділення чи дистанційно як первинний дилер Мінфіну, з державною гарантією.",
                        1000, UAH_USD_EUR, 13.5, 15.0, RiskLevel.LOW,
                        "https://www.oschadbank.ua/obligacii-vnutrisnoi-derzhavnoi-poziki-ovdp"),
                p("monobank-mil", "monobank", ProviderCategory.MILITARY_BOND,
                        "Купівля військових ОВДП у розділі Накопичення застосунку monobank без комісії, з погашенням на картку.",
                        1000, UAH_USD_EUR, 14.0, 16.2, RiskLevel.LOW, "https://monobank.ua/military-bonds"),
                p("sensebank-mil", "Sense Bank", ProviderCategory.MILITARY_BOND,
                        "Купівля військових гривневих і валютних ОВДП у Sense SuperApp або через Дію від 1000 грн.",
                        1000, UAH_USD_EUR, 14.0, 18.0, RiskLevel.LOW, "https://sensebank.ua/vijskovi-obligacii"),
                p("icu-mil", "ICU", ProviderCategory.MILITARY_BOND,
                        "Купівля військових ОВДП на платформі ICU Trade або через Дію без комісії за угоду, від однієї облігації.",
                        1000, UAH_USD_EUR, 14.0, 17.5, RiskLevel.LOW, "https://icu.ua/investments"),

                // --- Гривневі/валютні ОВДП (GOV_BOND) ---
                p("privatbank-ovdp", "ПриватБанк", ProviderCategory.GOV_BOND,
                        "Звичайні гривневі та валютні ОВДП у Приват24 з державною гарантією та податковою пільгою на купон.",
                        1000, UAH_USD_EUR, 3.0, 17.5, RiskLevel.LOW, "https://privatbank.ua/ovdp"),
                p("otpbank-ovdp", "OTP Bank", ProviderCategory.GOV_BOND,
                        "Купівля ОВДП у мобільному застосунку OTP Bank UA з первинного аукціону Мінфіну або портфеля банку.",
                        10000, UAH_USD_EUR, 3.2, 16.4, RiskLevel.LOW,
                        "https://www.otpbank.com.ua/privateclients/investments/ovdp-app/"),
                p("monobank-ovdp", "monobank", ProviderCategory.GOV_BOND,
                        "Гривневі та валютні ОВДП у застосунку monobank без комісії, з фіксованою дохідністю та виплатою на картку.",
                        1000, UAH_USD_EUR, 2.8, 16.2, RiskLevel.LOW, "https://monobank.ua/military-bonds"),
                p("kinto-ovdp", "КІНТО", ProviderCategory.GOV_BOND,
                        "Купівля гривневих ОВДП у Кабінеті інвестора КІНТО без комісії за угоду, оплата карткою, від 1000 грн.",
                        1000, UAH_USD_EUR, 14.0, 19.5, RiskLevel.LOW, "https://kinto.com/"),
                p("univer-ovdp", "Універ Капітал", ProviderCategory.GOV_BOND,
                        "Купівля ОВДП у застосунку UNIVER з комісією 1 грн за операцію, від 1000 грн, рахунок відкривають дистанційно.",
                        1000, UAH, 12.5, 16.6, RiskLevel.LOW, "https://www.univer.ua/univer-derzhavni-obligaciyi"),

                // --- Валюта готівкою (CASH_CURRENCY) ---
                p("privatbank-cash", "ПриватБанк", ProviderCategory.CASH_CURRENCY,
                        "Купівля доларів і євро готівкою в касі відділення за курсом банку; великі суми замовляють заздалегідь.",
                        1000, UAH_USD_EUR, 0.0, 3.0, RiskLevel.LOW, "https://privatbank.ua/obmin-valiut"),
                p("oschadbank-cash", "Ощадбанк", ProviderCategory.CASH_CURRENCY,
                        "Купівля готівкових доларів і євро в касі державного банку за касовим курсом у широкій мережі відділень.",
                        1000, UAH_USD_EUR, 0.0, 3.0, RiskLevel.LOW, "https://www.oschadbank.ua/currency-rate"),
                p("pumb-cash", "ПУМБ", ProviderCategory.CASH_CURRENCY,
                        "Купівля готівкових доларів і євро в касі ПУМБ із попереднім бронюванням у застосунку ПУМБ Online.",
                        1000, UAH_USD_EUR, 0.0, 3.0, RiskLevel.LOW, "https://www.pumb.ua/currency_order"),
                p("raiffeisen-cash", "Райффайзен Банк", ProviderCategory.CASH_CURRENCY,
                        "Купівля та продаж доларів і євро у відділеннях і онлайн через Raiffeisen Online для приватних клієнтів.",
                        1000, UAH_USD_EUR, 0.0, 3.0, RiskLevel.LOW, "https://raiffeisen.ua/"),
                p("sensebank-cash", "Sense Bank", ProviderCategory.CASH_CURRENCY,
                        "Обмін валюти у відділенні або в застосунку Sense SuperApp відповідно до правил НБУ.",
                        1000, UAH_USD_EUR, 0.0, 3.0, RiskLevel.LOW, "https://sensebank.ua/"),

                // --- Банківські метали (PRECIOUS_METALS) ---
                p("privatbank-metals", "ПриватБанк", ProviderCategory.PRECIOUS_METALS,
                        "Купівля та продаж злитків золота й срібла номіналами від 1 грама; з жовтня 2025 операції лише для клієнтів банку.",
                        4000, UAH, 0.0, 10.0, RiskLevel.MODERATE, "https://privatbank.ua/banking-metals"),
                p("ukrgasbank-metals", "Укргазбанк", ProviderCategory.PRECIOUS_METALS,
                        "Купівля штампованих злитків банківських металів найвищих проб від 1 грама до тройської унції за гривню.",
                        4000, UAH, 0.0, 10.0, RiskLevel.MODERATE, "https://www.ukrgasbank.com/private/bank_metals/"),
                p("otpbank-metals", "OTP Bank", ProviderCategory.PRECIOUS_METALS,
                        "Купівля банківського золота за гривню та відповідальне зберігання злитків для приватних клієнтів.",
                        4000, UAH, 0.0, 10.0, RiskLevel.MODERATE,
                        "https://otpbank.com.ua/privateclients/investments/bank-metals/"),
                p("mtbbank-metals", "МТБ БАНК", ProviderCategory.PRECIOUS_METALS,
                        "Купівля та продаж банківських дорогоцінних металів - злитків золота й срібла - для приватних клієнтів.",
                        4000, UAH, 0.0, 10.0, RiskLevel.MODERATE, "https://mtb.ua/BankingMetals"),
                p("kominbank-metals", "КОМІНБАНК", ProviderCategory.PRECIOUS_METALS,
                        "Купівля банківських злитків золота та відкриття рахунків у банківських металах для приватних клієнтів.",
                        4000, UAH, 0.0, 10.0, RiskLevel.MODERATE,
                        "https://cib.com.ua/uk/private/products/operaciji-z-bankivskimi-metalami"),

                // --- Нерухомість (REAL_ESTATE) ---
                p("kan-realty", "KAN Development", ProviderCategory.REAL_ESTATE,
                        "Великий київський забудовник: купівля квартири в новобудові комфорт- чи бізнес-класу для здачі в оренду.",
                        2500000, UAH_USD, 5.0, 10.0, RiskLevel.HIGH, "https://kandevelopment.com/"),
                p("saga-realty", "SAGA Development", ProviderCategory.REAL_ESTATE,
                        "Київський забудовник із десятками житлових комплексів: купівля квартири в новобудові для орендного доходу.",
                        2500000, UAH_USD, 5.0, 10.0, RiskLevel.HIGH, "https://saga-development.com.ua/"),
                p("intergalbud-realty", "Інтергал-Буд", ProviderCategory.REAL_ESTATE,
                        "Один із лідерів ринку новобудов: купівля квартири в ЖК у Києві чи регіонах для здачі в оренду.",
                        2000000, UAH_USD, 5.0, 10.0, RiskLevel.HIGH, "https://intergal-bud.com.ua/"),
                p("alliancenovobud-realty", "Alliance Novobud", ProviderCategory.REAL_ESTATE,
                        "Забудовник Києва та Броварів: купівля квартири бізнес-класу в новобудові передмістя для здачі в оренду.",
                        2000000, UAH_USD, 5.0, 10.0, RiskLevel.HIGH, "https://anbud.ua/"),
                p("dimria-realty", "DIM.RIA", ProviderCategory.REAL_ESTATE,
                        "Маркетплейс перевіреної нерухомості: пошук і купівля квартири з подальшим розміщенням оголошення про оренду.",
                        1000000, UAH_USD, 5.0, 10.0, RiskLevel.MODERATE, "https://dom.ria.com/uk/"),

                // --- Індексні ETF (INDEX_ETF) ---
                p("ibkr-etf", "Interactive Brokers", ProviderCategory.INDEX_ETF,
                        "Глобальний брокер: відкриття рахунку в IBKR LLC і купівля індексних ETF на S&P 500 чи світовий ринок.",
                        2000, USD_EUR, 7.0, 12.0, RiskLevel.MODERATE, "https://www.interactivebrokers.com/"),
                p("freedom24-etf", "Freedom24", ProviderCategory.INDEX_ETF,
                        "Брокер з українським корінням: доступ до 1500+ UCITS ETF на біржах Xetra та Euronext без мінімального депозиту.",
                        500, USD_EUR, 7.0, 12.0, RiskLevel.MODERATE, "https://freedom24.com/etf"),
                p("exante-etf", "Exante", ProviderCategory.INDEX_ETF,
                        "Мальтійський брокер: доступ до тисяч ETF із 50 бірж США, ЄС та Азії через єдиний мультивалютний рахунок.",
                        1500, USD_EUR, 7.0, 12.0, RiskLevel.MODERATE, "https://exante.eu/markets/"),
                p("xtb-etf", "XTB", ProviderCategory.INDEX_ETF,
                        "Регульований брокер з українським інтерфейсом: індексні ETF із нульовою комісією до місячного ліміту обороту.",
                        1000, USD_EUR, 7.0, 12.0, RiskLevel.MODERATE, "https://www.xtb.com/int"),
                p("saxo-etf", "Saxo Bank", ProviderCategory.INDEX_ETF,
                        "Данський інвестбанк: доступ до тисяч ETF на глобальних біржах через платформи SaxoInvestor та SaxoTraderGO.",
                        2000, USD_EUR, 7.0, 12.0, RiskLevel.MODERATE, "https://www.home.saxo/"),

                // --- Окремі іноземні акції (FOREIGN_STOCKS) ---
                p("ibkr-stocks", "Interactive Brokers", ProviderCategory.FOREIGN_STOCKS,
                        "Купівля окремих акцій США та інших ринків зі 135+ бірж через рахунок IBKR LLC з низькими комісіями.",
                        2000, USD_EUR, 8.0, 20.0, RiskLevel.HIGH, "https://www.interactivebrokers.com/"),
                p("freedom24-stocks", "Freedom24", ProviderCategory.FOREIGN_STOCKS,
                        "Доступ до понад мільйона акцій США та Європи з торгівлею на NYSE, Nasdaq і європейських біржах.",
                        500, USD_EUR, 8.0, 20.0, RiskLevel.HIGH, "https://freedom24.com/"),
                p("exante-stocks", "Exante", ProviderCategory.FOREIGN_STOCKS,
                        "Торгівля окремими акціями США, ЄС та Азії на 50 біржах через єдиний рахунок з комісією від кількох центів.",
                        1500, USD_EUR, 8.0, 20.0, RiskLevel.HIGH, "https://exante.eu/markets/"),
                p("xtb-stocks", "XTB", ProviderCategory.FOREIGN_STOCKS,
                        "Купівля реальних акцій США та ЄС із нульовою комісією до місячного ліміту обороту.",
                        1000, USD_EUR, 8.0, 20.0, RiskLevel.HIGH, "https://www.xtb.com/int"),
                p("etoro-stocks", "eToro", ProviderCategory.FOREIGN_STOCKS,
                        "Платформа з простим інтерфейсом: купівля іноземних акцій без комісії та дробовими частками (доступність для України варто перевірити при реєстрації).",
                        1500, USD_EUR, 8.0, 20.0, RiskLevel.HIGH, "https://www.etoro.com/"),

                // --- Криптовалюта (CRYPTO) ---
                p("whitebit-crypto", "WhiteBIT", ProviderCategory.CRYPTO,
                        "Найбільша європейська біржа з українським корінням: поповнення гривнею через картку та P2P, торгівля 300+ монетами.",
                        300, UAH_USD_EUR, 0.0, 60.0, RiskLevel.HIGH, "https://whitebit.com/"),
                p("kuna-crypto", "Kuna", ProviderCategory.CRYPTO,
                        "Українська за походженням біржа, одна з перших, що приймала гривню; орієнтована на інвесторів Східної Європи.",
                        300, UAH_USD_EUR, 0.0, 60.0, RiskLevel.HIGH, "https://kuna.io/"),
                p("binance-crypto", "Binance", ProviderCategory.CRYPTO,
                        "Найбільша світова біржа з ліцензією MiCA: купівля крипти за гривню через картку та P2P з низькою комісією.",
                        300, UAH_USD_EUR, 0.0, 60.0, RiskLevel.HIGH, "https://www.binance.com/"),
                p("kraken-crypto", "Kraken", ProviderCategory.CRYPTO,
                        "Біржа з акцентом на безпеку та Proof of Reserves: купівля крипти за гривню через картки та банки.",
                        300, UAH_USD_EUR, 0.0, 60.0, RiskLevel.HIGH, "https://www.kraken.com/"),
                p("okx-crypto", "OKX", ProviderCategory.CRYPTO,
                        "Глобальна біржа: поповнення гривнею через P2P та сторонніх провайдерів, з гривнею у застосунку.",
                        300, UAH_USD_EUR, 0.0, 60.0, RiskLevel.HIGH, "https://www.okx.com/"),

                // --- Корпоративні облігації (CORPORATE_BOND) ---
                p("icu-corp", "ICU", ProviderCategory.CORPORATE_BOND,
                        "Доступ до корпоративних облігацій українських емітентів через платформу ICU Trade або фонд облігацій.",
                        1000, UAH_USD, 16.0, 24.0, RiskLevel.MODERATE, "https://icu.ua/investments"),
                p("kinto-corp", "КІНТО", ProviderCategory.CORPORATE_BOND,
                        "Купівля корпоративних облігацій українських компаній через Кабінет інвестора КІНТО разом з ОВДП.",
                        1000, UAH, 16.0, 24.0, RiskLevel.MODERATE, "https://kinto.com/"),
                p("univer-corp", "Універ Капітал", ProviderCategory.CORPORATE_BOND,
                        "Купівля корпоративних облігацій у застосунку UNIVER з дохідністю до 24% річних.",
                        1000, UAH, 14.0, 24.0, RiskLevel.MODERATE, "https://www.univer.ua/products"),
                p("novapay-corp", "NovaPay", ProviderCategory.CORPORATE_BOND,
                        "Купівля корпоративних облігацій NovaPay у застосунку NovaPay від 1000 грн з дохідністю до 18% річних.",
                        1000, UAH, 12.0, 18.0, RiskLevel.MODERATE, "https://novapay.credit/invest/"),
                p("dragoncapital-corp", "Dragon Capital", ProviderCategory.CORPORATE_BOND,
                        "Брокерський доступ до корпоративних облігацій українських емітентів для приватних інвесторів.",
                        5000, UAH_USD, 16.0, 24.0, RiskLevel.MODERATE, "https://dragon-capital.com/ua/"),

                // --- Краудлендинг / P2P (CROWDLENDING) ---
                p("inzhur-p2p", "Inzhur", ProviderCategory.CROWDLENDING,
                        "Фонд нерухомості: купівля сертифікатів від 10 грн із щомісячними дивідендами з орендних платежів.",
                        10, UAH_USD, 9.0, 15.0, RiskLevel.HIGH, "https://www.inzhur.reit/"),
                p("smf-p2p", "Сімейні Молочні Ферми", ProviderCategory.CROWDLENDING,
                        "Імпакт-інвестування в агробізнес: внесок частки з щомісячними чи квартальними виплатами близько 21% річних.",
                        65000, UAH, 15.0, 25.0, RiskLevel.HIGH, "https://invest.smf.org.ua/"),
                p("fagura-p2p", "Fagura", ProviderCategory.CROWDLENDING,
                        "P2P-краудлендинг: позики бізнесу від 25 EUR з очікуваною дохідністю орієнтовно 10-20% річних.",
                        1100, EUR, 10.0, 20.0, RiskLevel.HIGH, "https://fagura.com/"),
                p("inventure-p2p", "InVenture", ProviderCategory.CROWDLENDING,
                        "Інвестиційний маркетплейс: вибір проєктів малого бізнесу для вкладення коштів з поверненням через відсотки чи зростання.",
                        50000, UAH_USD, 15.0, 30.0, RiskLevel.HIGH, "https://inventure.com.ua/"),
                p("mintos-p2p", "Mintos", ProviderCategory.CROWDLENDING,
                        "Європейський P2P-маркетплейс позик і бондів від 50 EUR: портфель кредитів різних оригінаторів з дохідністю від 10%.",
                        2200, EUR, 10.0, 14.0, RiskLevel.HIGH, "https://www.mintos.com/en/"),

                // --- Недержавні пенсійні фонди (PENSION_FUND) ---
                p("otpcapital-npf", "ОТП Пенсія (НПФ)", ProviderCategory.PENSION_FUND,
                        "Найбільший відкритий недержавний пенсійний фонд: періодичні внески інвестуються для додаткової пенсії.",
                        300, UAH, 10.0, 18.0, RiskLevel.MODERATE, "https://www.otpcapital.com.ua/"),
                p("privatfond-npf", "ПриватФонд (НПФ)", ProviderCategory.PENSION_FUND,
                        "Відкритий пенсійний фонд із внеском від 10 грн: накопичення на додаткову пенсію з довгостроковим інвестуванням.",
                        10, UAH, 10.0, 17.0, RiskLevel.MODERATE, "https://privatfond.com.ua/"),
                p("dynasty-npf", "Династія (НПФ, ICU)", ProviderCategory.PENSION_FUND,
                        "Відкритий НПФ під управлінням ICU: регулярні внески інвестуються в облігації для довгострокового накопичення пенсії.",
                        300, UAH, 10.0, 18.0, RiskLevel.MODERATE, "https://dynasty.icu/"),
                p("emeryt-npf", "Емерит-Україна (НПФ)", ProviderCategory.PENSION_FUND,
                        "Один із найбільших відкритих НПФ: пенсійні внески інвестуються для виплати додаткової пенсії.",
                        300, UAH, 10.0, 17.0, RiskLevel.MODERATE, "https://nfp.gov.ua/ua/NPF/3841.html"),
                p("socialstandard-npf", "Соціальний стандарт (НПФ)", ProviderCategory.PENSION_FUND,
                        "Відкритий НПФ для всіх охочих: акумулює добровільні внески й інвестує їх задля додаткової пенсії.",
                        300, UAH, 10.0, 17.0, RiskLevel.MODERATE, "https://www.acpo.com.ua/"),

                // --- Накопичувальне страхування життя (LIFE_INSURANCE) ---
                p("metlife-life", "MetLife Україна", ProviderCategory.LIFE_INSURANCE,
                        "Накопичувальне страхування життя: регулярні внески дають страховий захист і капітал з інвестдоходом наприкінці строку.",
                        5000, UAH_USD, 4.0, 9.0, RiskLevel.LOW, "https://ukrainelife.com.ua/"),
                p("uniqa-life", "UNIQA Life", ProviderCategory.LIFE_INSURANCE,
                        "Накопичувальне страхування життя: поєднання захисту життя й накопичення капіталу через довгострокові внески.",
                        4000, UAH_EUR, 4.0, 8.0, RiskLevel.LOW, "https://uniqa.ua/private/life/zhizn/"),
                p("tas-life", "ТАС Лайф", ProviderCategory.LIFE_INSURANCE,
                        "Програма TAS-LIFE: накопичення плюс захист, внески від 99 грн на рік, строк від 1 до 20 років.",
                        2000, UAH, 4.0, 9.0, RiskLevel.LOW, "https://taslife.com.ua/"),
                p("grawe-life", "GRAWE Україна", ProviderCategory.LIFE_INSURANCE,
                        "Накопичувальні програми страхування життя від австрійського концерну GRAWE з гарантованою виплатою наприкінці строку.",
                        4000, UAH_EUR, 4.0, 8.0, RiskLevel.LOW, "https://www.grawe.ua/"),
                p("kniazha-life", "Княжа Лайф VIG", ProviderCategory.LIFE_INSURANCE,
                        "Накопичувальне страхування життя та капіталу для дітей під Vienna Insurance Group: регулярні внески із захистом.",
                        4000, UAH, 4.0, 8.0, RiskLevel.LOW, "https://www.kniazha-life.com.ua/"),

                // --- Малий бізнес / стартапи (BUSINESS_EQUITY) ---
                p("iclub-equity", "ICLUB", ProviderCategory.BUSINESS_EQUITY,
                        "Ангельський синдикат: інвестиції від 5000 USD у відібрані стартапи разом з TA Ventures з отриманням частки.",
                        210000, USD, 0.0, 100.0, RiskLevel.HIGH, "https://iclub.vc/"),
                p("taventures-equity", "TA Ventures", ProviderCategory.BUSINESS_EQUITY,
                        "Венчурний фонд ранніх стадій: приватні особи долучаються до інвестицій у стартапи через синдикат ICLUB.",
                        210000, USD, 0.0, 100.0, RiskLevel.HIGH, "https://taventures.vc/"),
                p("aventures-equity", "AVentures Capital", ProviderCategory.BUSINESS_EQUITY,
                        "Український венчурний фонд для early- та growth-stage компаній: інвестиції в частки технологічних стартапів.",
                        4000000, USD, 0.0, 100.0, RiskLevel.HIGH, "https://aventurescapital.com/"),
                p("smrk-equity", "SMRK VC", ProviderCategory.BUSINESS_EQUITY,
                        "Венчурний фонд інвестицій в IT-стартапи різних стадій: вкладення в частки технологічних компаній.",
                        4000000, USD, 0.0, 100.0, RiskLevel.HIGH, "https://smrk.vc/en/"),
                p("flyerone-equity", "Flyer One Ventures", ProviderCategory.BUSINESS_EQUITY,
                        "Seed-стадійний венчурний фонд групи Genesis: інвестиції в частки європейських та українських стартапів.",
                        4000000, USD_EUR, 0.0, 100.0, RiskLevel.HIGH, "https://flyerone.vc/"));
    }

    /**
     * Original X7 seed slugs (four reconciled banks), superseded by the per-instrument catalog. They
     * carry pre-migration categories ({@code BANK_DEPOSIT}/{@code BROKER}) that no longer exist in
     * {@link ProviderCategory}, so they must be removed rather than left to render as dead labels.
     */
    private static final List<String> LEGACY_SLUGS = List.of("privatbank", "oschadbank", "otpbank", "monobank");

    @Override
    public void run(ApplicationArguments args) {
        removeStaleProviders();
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
     * One-time migration: drop catalog rows from the pre-{@code 13-instrument} taxonomy so the active
     * catalog matches the current {@link ProviderCategory} set. Removes (1) the superseded original
     * seed slugs and (2) defensively, any document whose {@code category} is not a current enum
     * constant (e.g. a leftover {@code FUND}/{@code OTHER} row). New per-instrument slugs
     * ({@code privatbank-mil}, ...) never collide with the legacy bare slugs, so the curated rows are
     * untouched. Inserted-only seeding then fills in the current catalog.
     */
    private void removeStaleProviders() {
        long bySlug = mongoTemplate.remove(
                Query.query(Criteria.where("_id").in(LEGACY_SLUGS)), Provider.class).getDeletedCount();
        List<String> validCategories = Arrays.stream(ProviderCategory.values()).map(Enum::name).toList();
        long byCategory = mongoTemplate.remove(
                Query.query(Criteria.where("category").nin(validCategories)), Provider.class).getDeletedCount();
        long removed = bySlug + byCategory;
        if (removed > 0) {
            log.info("Removed {} stale provider(s) from the pre-migration taxonomy before seeding.", removed);
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
