import { expect, test } from '@playwright/test';
import { signUp, uniqueEmail } from './support';

test('a supplier adds, edits, archives and restores a product', async ({ page }) => {
  await signUp(page, 'Catalog Hardware', uniqueEmail());

  await page
    .getByRole('navigation', { name: 'Main' })
    .getByRole('link', { name: 'Catalog' })
    .click();
  await expect(page.getByRole('heading', { name: 'No products yet' })).toBeVisible();

  // Add
  await page.getByRole('link', { name: 'Add product' }).first().click();
  await page.getByLabel('SKU').fill('SCREW-4X40');
  await page.getByLabel('Name').fill('Wood screw 4 x 40 mm');
  await page.getByLabel('Unit').fill('pcs');
  await page.getByLabel('Price').fill('0.0125');
  await page.getByLabel('Stock').fill('5000');
  await page.getByRole('button', { name: 'Add product' }).click();

  await expect(page).toHaveURL('/catalog');
  const row = page.getByRole('row', { name: /SCREW-4X40/ });
  await expect(row).toContainText('0.0125');
  await expect(row).toContainText('/ pcs');

  // Edit
  await row.getByRole('link', { name: 'Edit SCREW-4X40' }).click();
  await expect(page.getByLabel('Price')).toHaveValue('0.0125');
  await page.getByLabel('Price').fill('0.011');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('row', { name: /SCREW-4X40/ })).toContainText('0.011');

  // Archive
  await page.getByRole('button', { name: 'Archive SCREW-4X40' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Archive' }).click();
  await expect(page.getByRole('heading', { name: 'No products yet' })).toBeVisible();

  // Restore
  await page.getByRole('radio', { name: 'Archived' }).click();
  await page.getByRole('button', { name: 'Restore SCREW-4X40' }).click();
  await page.getByRole('radio', { name: 'Active' }).click();
  await expect(page.getByRole('row', { name: /SCREW-4X40/ })).toBeVisible();
});

test('a duplicate SKU is shown under the SKU field', async ({ page }) => {
  await signUp(page, 'Duplicate Traders', uniqueEmail());

  for (const name of ['First pipe', 'Second pipe']) {
    await page.goto('/catalog/new');
    await page.getByLabel('SKU').fill('PVC-20');
    await page.getByLabel('Name').fill(name);
    await page.getByLabel('Price').fill('4.50');
    await page.getByRole('button', { name: 'Add product' }).click();
  }

  await expect(page.getByText('Another product already uses SKU PVC-20.')).toBeVisible();
  await expect(page).toHaveURL('/catalog/new');
});
