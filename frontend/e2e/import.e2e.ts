import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { signUp, uniqueEmail } from './support';

async function expectAccessible(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
}

async function uploadCsv(page: Page, name: string, csv: string): Promise<void> {
  await page.goto('/catalog/import');
  await page.locator('input[type=file]').setInputFiles({
    name,
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf-8'),
  });
  await expect(page.getByRole('heading', { name: 'Match the columns' })).toBeVisible();
}

test('import a price list, fix the rows with errors and import them again', async ({ page }) => {
  // Two full imports and a download: longer than the default 30 s budget of one test.
  test.setTimeout(60_000);
  await signUp(page, 'Import Traders', uniqueEmail());

  // 1. Upload from the catalog page.
  await page
    .getByRole('navigation', { name: 'Main' })
    .getByRole('link', { name: 'Catalog' })
    .click();
  await page.getByRole('link', { name: 'Import CSV' }).first().click();
  await expect(page.getByRole('heading', { name: 'Import a price list' })).toBeVisible();
  await expectAccessible(page);

  await uploadCsv(
    page,
    'hardware.csv',
    [
      'Item code,Product name,Rate,Qty',
      'PVC-20,PVC pipe 20 mm,185.50,420',
      'PVC-25,PVC pipe 25 mm,240,310',
      'TAPE-01,PTFE tape,12.5O,640',
      'CEM-50,,2650,85',
      'SCREW-4,Wood screw,0.0125,12000',
    ].join('\n'),
  );

  // 2. Columns were recognised from their names.
  await expect(page.getByText('For example: PVC-20')).toBeVisible();
  await expectAccessible(page);
  await page.getByRole('button', { name: 'Import 5 rows' }).click();

  // 3. Result: 3 added, 2 rows to fix.
  await expect(page.getByRole('status')).toHaveText('Done, some rows have errors', {
    timeout: 30_000,
  });
  await expect(page.locator('.counts')).toContainText('Added3');
  const errorRows = page.getByRole('table', { name: 'Rows that could not be imported' });
  await expect(errorRows.getByRole('row')).toHaveCount(3);
  await expect(errorRows).toContainText('12.5O');
  await expectAccessible(page);

  // 4. Download the rows with errors, fix them, upload again.
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('link', { name: 'Download rows with errors (CSV)' }).click();
  const download = await downloadPromise;
  const report = (await readFile(await download.path())).toString('utf-8');
  expect(report).toContain('Item code,Product name,Rate,Qty,Error');
  const fixed = report
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/)
    .filter((line) => line.trim().length > 0)
    .map((line) => line.split(',').slice(0, 4))
    .map(([sku, name, rate, qty]) =>
      sku === 'Item code'
        ? [sku, name, rate, qty]
        : [sku, name || 'Portland cement 50 kg', rate.replace('O', '0'), qty],
    )
    .map((cells) => cells.join(','))
    .join('\n');

  await uploadCsv(page, 'hardware-fixed.csv', fixed);
  await page.getByRole('button', { name: 'Import 2 rows' }).click();
  await expect(page.getByRole('status')).toHaveText('Done', { timeout: 30_000 });

  // 5. All five products are in the catalog.
  await page.getByRole('link', { name: 'Go to catalog' }).click();
  await expect(page.getByRole('row')).toHaveCount(6);
  await expect(page.getByRole('row', { name: /CEM-50/ })).toContainText('Portland cement 50 kg');

  // 6. Both imports are in the history.
  await page.getByRole('link', { name: 'Imports', exact: true }).click();
  await expect(page.getByRole('link', { name: 'hardware-fixed.csv' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'hardware.csv', exact: true })).toBeVisible();
  await expectAccessible(page);
});
