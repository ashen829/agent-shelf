import { defineConfig, devices } from '@playwright/test';

const CI = !!process.env['CI'];

/**
 * End-to-end tests against the real backend and database. PostgreSQL must be running
 * (docker compose locally, a service container in CI); Playwright starts the backend jar and
 * the Angular dev server, or reuses ones that are already running locally.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: true,
  forbidOnly: CI,
  // One retry in CI so a single hiccup doesn't block a PR; retries are listed in the report.
  retries: CI ? 1 : 0,
  workers: CI ? 2 : undefined,
  reporter: CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      // Bundled Chromium by default (CI). Locally, E2E_CHANNEL=chrome uses the installed Chrome.
      use: { ...devices['Desktop Chrome'], channel: process.env['E2E_CHANNEL'] },
    },
  ],
  webServer: [
    {
      command: 'sh e2e/start-backend.sh',
      url: 'http://localhost:8080/actuator/health/readiness',
      reuseExistingServer: !CI,
      timeout: 120_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm start',
      url: 'http://localhost:4200',
      reuseExistingServer: !CI,
      timeout: 120_000,
    },
  ],
});
