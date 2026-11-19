import { request, type Page } from '@playwright/test'
import { clientIp, signIn, type Credentials } from '../support/api'
import { createUser, expect, test } from '../support/fixtures'
import { freshTotp } from '../support/totp'

/** A user with own password and two-factor sign-in set up, signed in once in a separate client. */
async function userWithTotp(baseURL: string) {
  const user = await createUser(baseURL, 'remember')
  const client = await request.newContext({ baseURL, extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } })
  await signIn(client, user)
  await client.dispose()
  return user
}

/** Signs in on the sign-in page with password and code, with „Angemeldet bleiben“ checked. */
async function signInRemembered(page: Page, user: Credentials) {
  await page.setExtraHTTPHeaders({ 'X-Forwarded-For': clientIp() })
  await page.goto('/login')
  await page.getByLabel('Benutzername').fill(user.username)
  await page.getByLabel('Passwort').fill(user.password)
  await page.getByRole('checkbox', { name: 'Angemeldet bleiben' }).check()
  await page.getByRole('button', { name: 'Weiter' }).click()
  await page.getByLabel('Code aus der Authenticator-App').fill(await freshTotp(user.secret!))
  await page.getByRole('button', { name: 'Anmelden' }).click()
  await expect(page).not.toHaveURL(/\/login/)
}

/** Simulates an expired session or a backend restart: only the session cookie is lost. */
async function loseSession(page: Page) {
  await page.context().clearCookies({ name: 'LOGIS_SESSION' })
}

test('stay signed in and sign out another device', async ({ page, browser, baseURL }) => {
  // Two sign-ins with a fresh TOTP code each: the second may wait for the next 30-second step.
  test.setTimeout(120_000)
  const user = await userWithTotp(baseURL!)

  await signInRemembered(page, user)
  await loseSession(page)
  await page.goto('/konto')
  await expect(page.getByRole('heading', { name: 'Mein Konto' })).toBeVisible()

  const other = await browser.newContext({ baseURL })
  const otherPage = await other.newPage()
  await signInRemembered(otherPage, user)

  const devices = page.getByRole('list', { name: 'Angemeldete Geräte' }).getByRole('listitem')
  await page.reload()
  await expect(devices).toHaveCount(2)
  await expect(devices.filter({ hasText: 'Dieses Gerät' })).toHaveCount(1)
  await devices.filter({ hasNotText: 'Dieses Gerät' }).getByRole('button', { name: /abmelden$/ }).click()
  await expect(devices).toHaveCount(1)

  // The other device has to sign in again once its session is gone.
  await loseSession(otherPage)
  await otherPage.goto('/konto')
  await expect(otherPage).toHaveURL(/\/login/)
  await other.close()

  // This device stays remembered.
  await loseSession(page)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Mein Konto' })).toBeVisible()
})
