import { expect, request as playwrightRequest, test as base, type APIRequestContext, type Browser, type Page } from '@playwright/test'
import { call, clientIp, signIn, unique, type Credentials } from './api'
import { ADMIN_STATE } from './global-setup'

export interface TestUser extends Credentials {
  displayName: string
  email: string
  id: number
}

/** Creates a user with a start password through the admin API (no sign-in yet). */
export async function createUser(baseURL: string, prefix = 'e2e'): Promise<TestUser> {
  const admin = await playwrightRequest.newContext({ baseURL, storageState: ADMIN_STATE })
  const username = unique(`${prefix}.`)
  const password = `Start-${username}-2026`
  const displayName = `Test ${username}`
  const email = `${username}@example.org`
  const created = await call<{ id: number }>(admin, 'POST', '/api/admin/users', { username, displayName, email, role: 'USER', password })
  await admin.dispose()
  return { username, password, displayName, email, id: created.id }
}

/** A further user, signed in in its own browser context (e.g. to see what a share allows). Close `page.context()`. */
export async function secondUser(browser: Browser, baseURL: string, prefix = 'e2e2') {
  const user = await createUser(baseURL, prefix)
  const context = await browser.newContext({ baseURL, extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } })
  await signIn(context.request, user)
  const page = await context.newPage()
  return { user, page, api: context.request }
}

/** A page signed in as the initial admin, for the user and category administration. Close `page.context()`. */
export async function adminPage(browser: Browser, baseURL: string) {
  const context = await browser.newContext({ baseURL, storageState: ADMIN_STATE, extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } })
  return context.newPage()
}

/** Signs out through the user menu of the app shell. */
export async function signOut(page: Page, username: string) {
  await openNavigation(page)
  await page.getByRole('button', { name: new RegExp(username) }).click()
  await page.getByRole('menuitem', { name: 'Abmelden' }).click()
  await expect(page).toHaveURL(/\/login/)
}

/** The password step of the sign-in page, from a fresh client IP (nginx limits sign-in requests per IP). */
export async function passwordStep(page: Page, username: string, password: string) {
  await page.setExtraHTTPHeaders({ 'X-Forwarded-For': clientIp() })
  await page.getByLabel('Benutzername').fill(username)
  await page.getByLabel('Passwort').fill(password)
  await page.getByRole('button', { name: 'Weiter' }).click()
}

type Fixtures = {
  /** A fresh user, signed in in the page's browser context. */
  user: TestUser
  /** API calls as that user (shares the browser's cookies). */
  api: APIRequestContext
}

export const test = base.extend<Fixtures>({
  // Own client IP per test (and retry): the sign-in rate limit of nginx counts per IP.
  extraHTTPHeaders: async ({ extraHTTPHeaders }, use) => {
    await use({ ...extraHTTPHeaders, 'X-Forwarded-For': clientIp() })
  },
  user: async ({ page, baseURL }, use) => {
    const user = await createUser(baseURL!)
    await signIn(page.context().request, user)
    await use(user)
  },
  api: async ({ page, user }, use) => {
    void user
    await use(page.context().request)
  },
})

export { expect }

/** Opens the navigation: on the phone it sits in a sheet behind the menu button (no-op when already open). */
export async function openNavigation(page: Page) {
  if (!isPhone(page) || (await page.getByRole('dialog').getByRole('link', { name: 'Übersicht' }).isVisible())) return
  await page.getByRole('button', { name: 'Menü umschalten' }).click()
  await page.getByRole('dialog').getByRole('link', { name: 'Übersicht' }).waitFor()
}

export function isPhone(page: Page) {
  return (page.viewportSize()?.width ?? 1280) < 768
}
