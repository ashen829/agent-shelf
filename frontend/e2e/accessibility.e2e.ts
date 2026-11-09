import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { signUp, uniqueEmail } from './support';

/** WCAG 2.1 A and AA rules: contrast, labels, landmarks, names of buttons and links, and more. */
async function expectNoViolations(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const summary = results.violations.map(
    (v) =>
      `${v.id} (${v.impact}): ${v.help} — ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`,
  );
  expect(summary).toEqual([]);
}

test('login and sign-up pages are accessible', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
  await expectNoViolations(page);

  await page.goto('/signup');
  await expect(page.getByRole('heading', { name: 'Create your supplier account' })).toBeVisible();
  await expectNoViolations(page);
});

test('signed-in pages are accessible', async ({ page }) => {
  await signUp(page, 'Accessible Supplies', uniqueEmail());
  await expectNoViolations(page);

  await page.goto('/catalog');
  await expect(page.getByRole('heading', { name: 'Catalog' })).toBeVisible();
  await expectNoViolations(page);

  await page.goto('/catalog/new');
  await expect(page.getByRole('heading', { name: 'Add product' })).toBeVisible();
  await expectNoViolations(page);
});

test.describe('on a phone', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('the compact layout is accessible', async ({ page }) => {
    await signUp(page, 'Small Screen Ltd', uniqueEmail());
    await expectNoViolations(page);
  });
});
