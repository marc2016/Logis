import { expect, request as playwrightRequest, test as base, type APIRequestContext, type Page } from '@playwright/test'
import { call, signIn, unique, type Credentials } from './api'
import { ADMIN_STATE } from './global-setup'

export interface TestUser extends Credentials {
  displayName: string
}

/** Creates a user with a start password through the admin API (no sign-in yet). */
export async function createUser(baseURL: string, prefix = 'e2e'): Promise<TestUser> {
  const admin = await playwrightRequest.newContext({ baseURL, storageState: ADMIN_STATE })
  const username = unique(`${prefix}.`)
  const user: TestUser = { username, password: `Start-${username}-2026`, displayName: `Test ${username}` }
  await call(admin, 'POST', '/api/admin/users', { username, displayName: user.displayName, role: 'USER', password: user.password })
  await admin.dispose()
  return user
}

type Fixtures = {
  /** A fresh user, signed in in the page's browser context. */
  user: TestUser
  /** API calls as that user (shares the browser's cookies). */
  api: APIRequestContext
}

export const test = base.extend<Fixtures>({
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
