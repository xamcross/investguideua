/** Investment search/history contract types (mirror the backend BE-S DTOs). */

export type SearchCurrency = 'UAH' | 'USD';
export type InvestmentHorizon = 'SHORT' | 'MEDIUM' | 'LONG';
export type RiskLevel = 'LOW' | 'MODERATE' | 'HIGH';
/** Advisor free-text output language; mirrors the backend SearchLanguage enum. */
export type SearchLanguage = 'UK' | 'EN';
/**
 * Investment-instrument types (mirror the backend ProviderCategory enum). The `/providers` screen
 * groups the catalog by this value; the search results badge renders it via the `category.*` i18n keys.
 */
export type ProviderCategory =
  | 'MILITARY_BOND'
  | 'GOV_BOND'
  | 'CASH_CURRENCY'
  | 'PRECIOUS_METALS'
  | 'REAL_ESTATE'
  | 'INDEX_ETF'
  | 'FOREIGN_STOCKS'
  | 'CRYPTO'
  | 'CORPORATE_BOND'
  | 'CROWDLENDING'
  | 'PENSION_FUND'
  | 'LIFE_INSURANCE'
  | 'BUSINESS_EQUITY';
export type SearchStatus = 'pending' | 'completed' | 'failed';

export interface ReturnRange {
  min: number;
  max: number;
}

/**
 * Request body for POST /investments/search.
 * `amount` is integer MINOR UNITS (kopiykas) — the form collects UAH and multiplies by 100 before
 * sending, matching the backend "money is always integer minor units" rule.
 */
export interface SearchRequestBody {
  amount: number;
  currency: SearchCurrency;
  horizon?: InvestmentHorizon | null;
  riskTolerance?: RiskLevel | null;
  goals?: string | null;
  /** Selected UI language; the advisor returns instrument/rationale text in this language. */
  language?: SearchLanguage;
}

export interface InvestmentOption {
  providerId: string;
  providerName: string;
  instrument: string;
  /** Instrument type; `null` for legacy searches whose category predates the 13-instrument taxonomy. */
  category: ProviderCategory | null;
  currency: SearchCurrency;
  expectedReturnPct: ReturnRange;
  riskLevel: RiskLevel;
  /** Minor units (kopiykas); format for display. */
  minAmount: number;
  liquidity: string;
  rationale: string;
  sourceUrl: string;
  /** Precious-metals grounding (feature 011): set only for a PRECIOUS_METALS option. */
  metal?: 'GOLD' | 'SILVER' | null;
  /** Exact current bank sale rate in minor units (kopiykas) per gram; null unless a metals option. */
  metalPricePerGramMinor?: number | null;
  /** Bond grounding (feature 012): the grounded bond's ISIN; null unless a GOV_BOND/MILITARY_BOND option. */
  bondIsin?: string | null;
  /** Exact stored sell price in minor units (kopiykas) per 1000 face value; null unless a bond option. */
  bondSellPriceMinor?: number | null;
}

/** Response from POST /investments/search and GET /investments/{id} (rendered identically). */
export interface SearchResponse {
  requestId: string;
  tokenBalance: number;
  amount: number;
  currency: SearchCurrency;
  options: InvestmentOption[];
  disclaimer: string;
  currencyRiskDisclaimer?: string;
}

export interface SearchInputSummary {
  amount: number;
  currency: SearchCurrency;
  horizon?: InvestmentHorizon | null;
  riskTolerance?: RiskLevel | null;
  goals?: string | null;
}

export interface HistoryItem {
  id: string;
  createdAt: string;
  status: SearchStatus;
  input: SearchInputSummary;
  optionCount: number;
}

export interface HistoryPage {
  items: HistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
