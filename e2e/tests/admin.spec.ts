import { request } from '@playwright/test'
import { call, clientIp, signIn, unique } from '../support/api'
import { adminPage, expect, test } from '../support/fixtures'

type Stage = { stage: string }

/** The first step of a sign-in through the API, from a fresh client. */
async function loginStage(baseURL: string, username: string, password: string) {
  const client = await request.newContext({ baseURL, extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } })
  try {
    await call(client, 'GET', '/api/auth/me')
    return await call<Stage>(client, 'POST', '/api/auth/login', { username, password })
  } catch (e) {
    return { stage: String(e) }
  } finally {
    await client.dispose()
  }
}

test('create a user, disable, enable and reset two-factor sign-in', async ({ browser, baseURL }) => {
  const admin = await adminPage(browser, baseURL!)
  const username = unique('neu.')
  const password = `Start-${username}-2026`
  await admin.goto('/einstellungen/benutzer')
  await admin.getByRole('button', { name: 'Benutzer anlegen' }).click()
  const dialog = admin.getByRole('dialog', { name: 'Benutzer anlegen' })
  await dialog.getByLabel('Benutzername').fill(username)
  await dialog.getByLabel('Anzeigename').fill(`Test ${username}`)
  await dialog.getByLabel('E-Mail-Adresse').fill(`${username}@example.org`)
  await dialog.getByLabel('Startpasswort').fill(password)
  await dialog.getByRole('button', { name: 'Anlegen' }).click()
  await expect(dialog).toBeHidden()
  await expect(admin.getByText(username, { exact: true })).toBeVisible()

  // First sign-in of the new user: own password and two-factor setup.
  const credentials = { username, password }
  const client = await request.newContext({ baseURL, extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } })
  await signIn(client, credentials)
  await client.dispose()

  const actions = () => admin.getByRole('button', { name: `Aktionen für ${username}` })
  await actions().click()
  await admin.getByRole('menuitem', { name: 'Deaktivieren' }).click()
  await admin.getByRole('dialog').getByRole('button', { name: 'Deaktivieren' }).click()
  expect((await loginStage(baseURL!, username, credentials.password)).stage).toContain('401')

  await actions().click()
  await admin.getByRole('menuitem', { name: 'Reaktivieren' }).click()
  await admin.getByRole('dialog').getByRole('button', { name: 'Reaktivieren' }).click()
  expect((await loginStage(baseURL!, username, credentials.password)).stage).toBe('TOTP_REQUIRED')

  await actions().click()
  await admin.getByRole('menuitem', { name: '2FA zurücksetzen' }).click()
  await admin.getByRole('dialog').getByRole('button', { name: '2FA zurücksetzen' }).click()
  await expect.poll(async () => (await loginStage(baseURL!, username, credentials.password)).stage).toBe('TOTP_ENROLLMENT_REQUIRED')
  await admin.context().close()
})

test('create and archive a category', async ({ browser, baseURL }) => {
  const admin = await adminPage(browser, baseURL!)
  const name = unique('Kategorie ')
  await admin.goto('/einstellungen/kategorien')
  await admin.getByRole('region', { name: 'Ausgaben' }).getByRole('button', { name: 'Hauptkategorie' }).click()
  const dialog = admin.getByRole('dialog', { name: 'Kategorie anlegen (Ausgaben)' })
  await dialog.getByLabel('Name').fill(name)
  await dialog.getByRole('button', { name: 'Speichern' }).click()
  await expect(dialog).toBeHidden()

  await admin.getByRole('button', { name: `${name} archivieren` }).click()
  await expect(admin.getByRole('button', { name: `${name} wiederherstellen` })).toBeVisible()
  await admin.context().close()
})
