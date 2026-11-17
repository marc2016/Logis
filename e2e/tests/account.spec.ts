import { expect, openNavigation, passwordStep, signOut, test } from '../support/fixtures'
import { freshTotp } from '../support/totp'

test('change the password and sign in with the new one', async ({ page, user }) => {
  const newPassword = `${user.password}-geaendert`
  await page.goto('/konto')
  await page.getByLabel('Aktuelles Passwort').fill(user.password)
  await page.getByLabel('Neues Passwort', { exact: true }).fill(newPassword)
  await page.getByLabel('Neues Passwort wiederholen').fill(newPassword)
  await page.getByRole('button', { name: 'Passwort ändern' }).click()
  await expect(page.getByText('Passwort geändert. Andere Sitzungen wurden beendet.')).toBeVisible()

  await signOut(page, user.username)
  await passwordStep(page, user.username, user.password)
  await expect(page.getByText('Benutzername oder Passwort ist falsch')).toBeVisible()
  await passwordStep(page, user.username, newPassword)
  await page.getByLabel('Code aus der Authenticator-App').fill(await freshTotp(user.secret!))
  await page.getByRole('button', { name: 'Anmelden' }).click()
  await openNavigation(page)
  await expect(page.getByRole('link', { name: 'Übersicht' })).toBeVisible()
})

test('generate new recovery codes and sign in with one of them', async ({ page, user }) => {
  await page.goto('/konto')
  await page.getByRole('button', { name: 'Neue Codes erzeugen' }).click()
  await page.getByRole('dialog', { name: 'Neue Wiederherstellungscodes erzeugen?' }).getByRole('button', { name: 'Neue Codes erzeugen' }).click()
  const codes = page.getByRole('list', { name: 'Wiederherstellungscodes' }).getByRole('listitem')
  await expect(codes).toHaveCount(10)
  const code = (await codes.first().textContent())!.trim()

  await signOut(page, user.username)
  await passwordStep(page, user.username, user.password)
  await page.getByRole('button', { name: 'Wiederherstellungscode verwenden' }).click()
  await page.getByLabel('Wiederherstellungscode').fill(code)
  await page.getByRole('button', { name: 'Anmelden' }).click()
  await openNavigation(page)
  await expect(page.getByRole('link', { name: 'Übersicht' })).toBeVisible()
})
