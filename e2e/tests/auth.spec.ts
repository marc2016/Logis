import type { Page } from '@playwright/test'
import { createUser, expect, openNavigation, test } from '../support/fixtures'
import { freshTotp } from '../support/totp'

async function firstStep(page: Page, username: string, password: string) {
  await expect(page).toHaveURL(/\/login/)
  await page.getByLabel('Benutzername').fill(username)
  await page.getByLabel('Passwort').fill(password)
  await page.getByRole('button', { name: 'Weiter' }).click()
}

async function signOut(page: Page, username: string) {
  await openNavigation(page)
  await page.getByRole('button', { name: new RegExp(username) }).click()
  await page.getByRole('menuitem', { name: 'Abmelden' }).click()
}

async function expectSignedIn(page: Page) {
  await openNavigation(page)
  await expect(page.getByRole('link', { name: 'Übersicht' })).toBeVisible()
}

test('first sign-in with two-factor setup, recovery codes and own password', async ({ page, baseURL }) => {
  const user = await createUser(baseURL!, 'auth')
  const password = `${user.password}-eigenes`

  await page.goto('/')
  await firstStep(page, user.username, user.password)

  await expect(page.getByText('Zwei-Faktor-Anmeldung einrichten')).toBeVisible()
  await expect(page.getByRole('img', { name: 'QR-Code für die Authenticator-App' })).toBeVisible()
  const secret = (await page.getByTestId('totp-secret').textContent())!.trim()
  await page.getByLabel('Code aus der Authenticator-App').fill(await freshTotp(secret))
  await page.getByRole('button', { name: 'Bestätigen' }).click()

  const codes = page.getByRole('list', { name: 'Wiederherstellungscodes' }).getByRole('listitem')
  await expect(codes).toHaveCount(10)
  const recovery = (await codes.first().textContent())!.trim()
  await page.getByRole('button', { name: 'Ich habe die Codes gesichert' }).click()

  // The start password from the admin must be replaced.
  await page.getByLabel('Neues Passwort').fill(password)
  await page.getByLabel('Passwort wiederholen').fill(password)
  await page.getByRole('button', { name: 'Passwort speichern' }).click()
  await expectSignedIn(page)

  // Sign out and in again with a code from the app.
  await signOut(page, user.username)
  await firstStep(page, user.username, password)
  await page.getByLabel('Code aus der Authenticator-App').fill(await freshTotp(secret))
  await page.getByRole('button', { name: 'Anmelden' }).click()
  await expectSignedIn(page)

  // A recovery code instead of the app.
  await signOut(page, user.username)
  await firstStep(page, user.username, password)
  await page.getByRole('button', { name: 'Wiederherstellungscode verwenden' }).click()
  await page.getByLabel('Wiederherstellungscode').fill(recovery)
  await page.getByRole('button', { name: 'Anmelden' }).click()
  await expectSignedIn(page)
})
