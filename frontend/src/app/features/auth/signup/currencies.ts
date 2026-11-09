export interface CurrencyOption {
  code: string;
  label: string;
}

/** Currencies most of our pilot suppliers use, shown at the top of the list. */
const COMMON = ['USD', 'EUR', 'GBP', 'LKR', 'INR', 'AUD', 'CAD', 'SGD', 'AED', 'JPY'];

/** All ISO 4217 currencies the browser knows, common ones first, each with its English name. */
export function currencyOptions(): { common: CurrencyOption[]; others: CurrencyOption[] } {
  const names = new Intl.DisplayNames(['en'], { type: 'currency' });
  const option = (code: string): CurrencyOption => ({
    code,
    label: `${code} – ${names.of(code) ?? code}`,
  });
  const all = Intl.supportedValuesOf('currency');
  return {
    common: COMMON.filter((code) => all.includes(code)).map(option),
    others: all.filter((code) => !COMMON.includes(code)).map(option),
  };
}
