import { request } from '@playwright/test'
import { clientIp, signIn } from '../support/api'
import { createUser, expect, passwordStep, test } from '../support/fixtures'

/** A user with two-factor sign-in set up, signed in once in a separate client. */
async function userWithTotp(baseURL: string) {
  const user = await createUser(baseURL, 'signin')
  const client = await request.newContext({ baseURL, extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } })
  await signIn(client, user)
  await client.dispose()
  return user
}

test('leave the code step with „Zurück zur Anmeldung“', async ({ page, baseURL }) => {
  const user = await userWithTotp(baseURL!)
  await page.goto('/login')
  await passwordStep(page, user.username, user.password)
  await expect(page.getByText('Bestätigungscode')).toBeVisible()

  await page.getByRole('button', { name: 'Zurück zur Anmeldung' }).click()
  await expect(page.getByLabel('Benutzername')).toHaveValue('')
  // The unfinished sign-in ended on the server: a reload stays on the password step.
  await page.reload()
  await expect(page.getByLabel('Benutzername')).toBeVisible()
  await expect(page.getByText('Bestätigungscode')).toBeHidden()
})

test('five wrong passwords lock the user', async ({ page, baseURL }) => {
  const user = await userWithTotp(baseURL!)
  await page.goto('/login')
  for (let i = 0; i < 5; i++) {
    await passwordStep(page, user.username, `falsch-${i}-passwort`)
    await expect(page.getByText('Benutzername oder Passwort ist falsch')).toBeVisible()
  }
  await passwordStep(page, user.username, user.password)
  await expect(page.getByText('Zu viele Fehlversuche, bitte später erneut versuchen')).toBeVisible()
  await expect(page.getByText('Bestätigungscode')).toBeHidden()
})
