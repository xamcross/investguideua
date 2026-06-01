import { ProviderCategory, ReturnRange, RiskLevel } from '../investment/investment.models';

/**
 * Active provider catalog entry (mirrors the backend `ProviderResponse`, ticket BE-C2 / FE-ACCT2).
 * `minAmount` is integer minor units (kopiykas); format for display.
 */
export interface Provider {
  id: string;
  name: string;
  category: ProviderCategory;
  description: string;
  minAmount: number;
  maxAmount: number | null;
  currencies: string[];
  typicalReturnPct: ReturnRange;
  riskLevel: RiskLevel;
  sourceUrl: string;
}
