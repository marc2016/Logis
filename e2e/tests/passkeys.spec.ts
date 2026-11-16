import { expect, openNavigation, test } from '../support/fixtures'

test('add a passkey and sign in with it', async ({ page, user }) => {
  // A virtual platform authenticator of Chromium stands in for Touch ID.
  const cdp = await page.context().newCDPSession(page)
  await cdp.send('WebAuthn.enable')
  await cdp.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: 'ctap2',
      transport: 'internal',
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true,
    },
  })

  await page.goto('/konto')
  await page.getByRole('button', { name: 'Passkey hinzufügen' }).click()
  const dialog = page.getByRole('dialog', { name: 'Passkey hinzufügen' })
  await dialog.getByLabel('Aktuelles Passwort').fill(user.password)
  await dialog.getByLabel('Name').fill('Testgerät')
  await dialog.getByRole('button', { name: 'Hinzufügen' }).click()
  await expect(page.getByRole('list', { name: 'Meine Passkeys' })).toContainText('Testgerät')

  // Sign out, then sign in with the passkey alone.
  await openNavigation(page)
  await page.getByRole('button', { name: new RegExp(user.username) }).click()
  await page.getByRole('menuitem', { name: 'Abmelden' }).click()
  await expect(page).toHaveURL(/\/login/)
  await page.getByRole('button', { name: 'Mit Passkey anmelden' }).click()
  await expect(page).not.toHaveURL(/\/login/)
  await openNavigation(page)
  await expect(page.getByRole('link', { name: 'Übersicht' })).toBeVisible()
  await page.goto('/konto')
  await expect(page.getByRole('list', { name: 'Meine Passkeys' })).toContainText('Zuletzt verwendet')
})
