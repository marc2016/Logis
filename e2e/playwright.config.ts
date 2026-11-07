import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests against a running Logis stack (see README, "End-to-end tests").
 * LOGIS_E2E_URL: entry point of the stack, default the nginx port of compose.yml.
 */
export default defineConfig({
  testDir: './tests',
  globalSetup: './support/global-setup.ts',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  // One worker: TOTP codes can be used only once per 30 seconds and the stack is small.
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.LOGIS_E2E_URL ?? 'http://localhost:8080',
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: process.env.LOGIS_E2E_CHROMIUM ? { executablePath: process.env.LOGIS_E2E_CHROMIUM } : {},
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    { name: 'phone', use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true } },
  ],
})
