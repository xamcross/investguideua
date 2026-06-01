/** Investment search/history contract types (mirror the backend BE-S DTOs). */

export type SearchCurrency = 'UAH' | 'USD';
export type InvestmentHorizon = 'SHORT' | 'MEDIUM' | 'LONG';
export type RiskLevel = 'LOW' | 'MODERATE' | 'HIGH';
/** Advisor free-text output language; mirrors the backend SearchLanguage enum. */
export type SearchLanguage = 'UK' | 'EN';
export type ProviderCategory = 'BANK_DEPOSIT' | 'GOV_BOND' | 'BROKER' | 'FUND' | 'OTHER';
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
  category: ProviderCategory;
  currency: SearchCurrency;
  expectedReturnPct: ReturnRange;
  riskLevel: RiskLevel;
  /** Minor units (kopiykas); format for display. */
  minAmount: number;
  liquidity: string;
  rationale: string;
  sourceUrl: string;
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
