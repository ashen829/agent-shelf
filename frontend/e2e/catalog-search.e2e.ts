import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { signUp, uniqueEmail } from './support';

async function importProducts(page: Page): Promise<void> {
  await page.goto('/catalog/import');
  await page.locator('input[type=file]').setInputFiles({
    name: 'search-demo.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(
      [
        'SKU,Name,Category,Price,Stock',
        'PVC-20,PVC pipe 20 mm,Pipes,185.50,420',
        'PVC-25,PVC pipe 25 mm,Pipes,240,0',
        'SCREW-4X40,Wood screw 4 x 40 mm,Fasteners,2.75,12000',
        'BOLT-M8,Hex bolt M8,Fasteners,45,900',
        'CEM-50,Portland cement 50 kg,Building materials,2650,0',
        'TAPE-PTFE,PTFE thread seal tape,,95,640',
      ].join('\n'),
      'utf-8',
    ),
  });
  await page.getByRole('button', { name: 'Import 6 rows' }).click();
  await expect(page.getByRole('status')).toHaveText('Done', { timeout: 30_000 });
}

function skuCells(page: Page) {
  return page.getByRole('row').locator('td:first-child');
}

test('search, filter and sort the catalog; the URL keeps it', async ({ page }) => {
  test.setTimeout(60_000);
  await signUp(page, 'Search Supplies', uniqueEmail());
  await importProducts(page);
  await page.goto('/catalog');
  await expect(skuCells(page)).toHaveCount(6);

  // Every word must match: SKU, name or category, any case.
  await page.getByRole('searchbox', { name: 'Search' }).fill('PVC 20');
  await expect(skuCells(page)).toHaveText(['PVC-20']);
  await expect(page).toHaveURL(/q=PVC(\+|%20)20/);

  // No match -> clear.
  await page.getByRole('searchbox', { name: 'Search' }).fill('unobtainium');
  await expect(page.getByRole('heading', { name: 'No products match' })).toBeVisible();
  await page.getByRole('button', { name: 'Clear search and filters' }).click();
  await expect(skuCells(page)).toHaveCount(6);

  // Category (with counts) and stock filters.
  await page.getByRole('combobox', { name: 'Category' }).click();
  await page.getByRole('option', { name: 'Fasteners (2)' }).click();
  await expect(skuCells(page)).toHaveText(['BOLT-M8', 'SCREW-4X40']);
  await page.getByRole('combobox', { name: 'Category' }).click();
  await page.getByRole('option', { name: 'All categories' }).click();
  await page.getByRole('combobox', { name: 'Stock' }).click();
  await page.getByRole('option', { name: 'Out of stock' }).click();
  await expect(skuCells(page)).toHaveText(['CEM-50', 'PVC-25']);

  // Sort by price, and the URL keeps everything across a reload.
  await page.getByRole('combobox', { name: 'Sort by' }).click();
  await page.getByRole('option', { name: 'Price: high to low' }).click();
  await expect(skuCells(page)).toHaveText(['CEM-50', 'PVC-25']);
  await page.reload();
  await expect(skuCells(page)).toHaveText(['CEM-50', 'PVC-25']);
  await expect(page.getByRole('combobox', { name: 'Stock' })).toContainText('Out of stock');

  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
});
