import { trimZeros } from './product-form/product-form';
import { formatPrice, PRICE_PATTERN } from './money';

describe('formatPrice', () => {
  it('keeps fractions of a cent', () => {
    expect(formatPrice('0.0125', 'USD', 'en-US')).toBe('$0.0125');
  });

  it('shows at least the usual decimals of the currency', () => {
    expect(formatPrice('12.5000', 'USD', 'en-US')).toBe('$12.50');
    expect(formatPrice('1500.0000', 'JPY', 'en-US')).toBe('¥1,500');
  });
});

describe('PRICE_PATTERN', () => {
  it.each(['0', '12', '12.5', '0.0125', '1234567890123.9999'])('accepts %s', (v) => {
    expect(PRICE_PATTERN.test(v)).toBe(true);
  });

  it.each(['', '-1', '1.23456', '1,50', '12.', '.5', '1e3'])('rejects %j', (v) => {
    expect(PRICE_PATTERN.test(v)).toBe(false);
  });
});

describe('trimZeros', () => {
  it.each([
    ['12.5000', '12.50'],
    ['0.0125', '0.0125'],
    ['10.0000', '10.00'],
    ['4.7500', '4.75'],
  ])('%s -> %s', (input, output) => {
    expect(trimZeros(input)).toBe(output);
  });
});
