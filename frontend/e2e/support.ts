import { expect, type Page } from '@playwright/test';

/** Test-only password for accounts created by these tests in a throwaway database. */
export const PASSWORD = 'e2e only password 123';

/** A new email per test, so tests never clash and can run in parallel. */
export function uniqueEmail(): string {
  return `e2e-${crypto.randomUUID()}@agentshelf.test`;
}

export async function signUp(page: Page, company: string, email: string): Promise<void> {
  await page.goto('/signup');
  await page.getByLabel('Company name').fill(company);
  await page.getByRole('combobox', { name: 'Currency for your prices' }).click();
  await page.getByRole('option', { name: /^LKR/ }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: `Welcome, ${company}` })).toBeVisible();
}

export async function logIn(page: Page, email: string, password = PASSWORD): Promise<void> {
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
}

export async function logOut(page: Page, company: string): Promise<void> {
  await page.getByRole('button', { name: `Account menu for ${company}` }).click();
  await page.getByRole('menuitem', { name: 'Log out' }).click();
  await expect(page).toHaveURL('/login');
}
