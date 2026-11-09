import { expect, test } from '@playwright/test';
import { logIn, logOut, signUp, uniqueEmail } from './support';

test('a new supplier signs up and lands on their dashboard', async ({ page }) => {
  await signUp(page, 'Lanka Pipes', uniqueEmail());

  await expect(page).toHaveURL('/');
  await expect(page.getByText('Prices in LKR (Sri Lankan Rupee)')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Account menu for Lanka Pipes' })).toBeVisible();
  await expect(
    page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Dashboard' }),
  ).toHaveAttribute('aria-current', 'page');
});

test('signed-out visitors go to login and come back after signing in', async ({
  page,
  browser,
}) => {
  const email = uniqueEmail();
  const other = await browser.newContext();
  await signUp(await other.newPage(), 'Returning Ltd', email);
  await other.close();

  await page.goto('/');
  await expect(page).toHaveURL('/login?returnUrl=%2F');

  await logIn(page, email);
  await expect(page).toHaveURL('/');
  await expect(page.getByRole('heading', { name: 'Welcome, Returning Ltd' })).toBeVisible();
});

test('the session survives a page reload', async ({ page }) => {
  await signUp(page, 'Reload Traders', uniqueEmail());

  await page.reload();

  await expect(page.getByRole('heading', { name: 'Welcome, Reload Traders' })).toBeVisible();
});

test('logging in again right after logging out works', async ({ page }) => {
  const email = uniqueEmail();
  await signUp(page, 'Relogin Supplies', email);

  await logOut(page, 'Relogin Supplies');
  // Same page, no reload: this needs a fresh CSRF token from the logout response.
  await logIn(page, email);

  await expect(page.getByRole('heading', { name: 'Welcome, Relogin Supplies' })).toBeVisible();
});

test('a wrong password shows the generic error', async ({ page, browser }) => {
  const email = uniqueEmail();
  const other = await browser.newContext();
  await signUp(await other.newPage(), 'Careful Co', email);
  await other.close();

  await page.goto('/login');
  await logIn(page, email, 'not the right password');

  await expect(page.getByRole('alert')).toHaveText('Email or password is incorrect.');
  await expect(page).toHaveURL('/login');
});
