import { safeReturnUrl } from './return-url';

describe('safeReturnUrl', () => {
  it.each(['/', '/catalog', '/quotes/42?tab=items', '/a#b'])('keeps the app path %s', (url) => {
    expect(safeReturnUrl(url)).toBe(url);
  });

  it.each([
    [undefined],
    [null],
    [''],
    ['catalog'],
    ['//evil.example'],
    ['/\\evil.example'],
    ['https://evil.example'],
    ['javascript:alert(1)'],
    ['/\t/evil.example'],
    ['/%0a'.replace('%0a', '\n')],
    ['/login'],
    ['/login?returnUrl=%2F'],
    ['/signup'],
  ])('falls back to / for %j', (url) => {
    expect(safeReturnUrl(url)).toBe('/');
  });
});
