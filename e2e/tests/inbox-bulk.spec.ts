import { holidayHome } from '../support/data'
import { expect, test } from '../support/fixtures'
import { connectMailbox, deliver, fetchMails, fewoConfirmation } from '../support/mail'

test('assign one unit to several waiting mails at once', async ({ page, api, user }) => {
  const home = await holidayHome(api)
  const address = `${user.username}@example.org`
  await connectMailbox(api, address)
  const base = String(Date.now()).slice(-5)
  await deliver(address, fewoConfirmation({ to: address, object: home.name, ref: `${base}1`, arrival: '2030-10-01', departure: '2030-10-04', guest: 'Ida Testgast' }))
  await deliver(address, fewoConfirmation({ to: address, object: home.name, ref: `${base}2`, arrival: '2030-10-10', departure: '2030-10-12', guest: 'Theo Testgast' }))
  await fetchMails(api, /Neue Buchung/, 2)

  await page.goto('/posteingang')
  await expect(page.getByRole('row', { name: /^Mail Neue Buchung/ })).toHaveCount(2)
  await page.getByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }).click()
  const bar = page.getByRole('toolbar', { name: 'Aktionen für die Auswahl' })
  await bar.getByRole('button', { name: 'Einheit zuordnen' }).click()
  const dialog = page.getByRole('dialog', { name: 'Einheit zuordnen' })
  await dialog.getByRole('combobox', { name: 'Einheit' }).click()
  await page.getByRole('option', { name: home.name }).click()
  await dialog.getByRole('button', { name: 'Zuordnen' }).click()
  await expect(page.getByText('2 zugeordnet')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'Buchung angelegt', exact: true })).toHaveCount(2)
})
