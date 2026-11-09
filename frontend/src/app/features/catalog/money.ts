/**
 * Formats a price from the API (decimal text, up to 4 places) for display. Shows at least the
 * currency's usual decimals and up to 4, so 0.0125 USD stays "$0.0125" and 12.5 is "$12.50".
 */
export function formatPrice(amount: string, currency: string, locale?: string): string {
  const usual =
    new Intl.NumberFormat(locale, { style: 'currency', currency }).resolvedOptions()
      .minimumFractionDigits ?? 2;
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    minimumFractionDigits: usual,
    maximumFractionDigits: Math.max(usual, 4),
  }).format(Number(amount));
}

/** Decimal text the API accepts: up to 13 digits, then up to 4 decimals. */
export const PRICE_PATTERN = /^\d{1,13}(\.\d{1,4})?$/;
