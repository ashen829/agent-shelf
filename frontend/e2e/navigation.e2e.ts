import { expect, test } from '@playwright/test';
import { signUp, uniqueEmail } from './support';

test.describe('on a phone', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('the navigation opens from the menu button', async ({ page }) => {
    await signUp(page, 'Mobile Hardware', uniqueEmail());
    const nav = page.getByRole('navigation', { name: 'Main' });
    await expect(nav).toBeHidden();

    await page.getByRole('button', { name: 'Menu', exact: true }).click();

    await expect(nav).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Coming soon' })).toBeVisible();
  });
});
