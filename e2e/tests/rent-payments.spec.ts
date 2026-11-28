import { account, germanDate, longTermUnit, owner, tenancy } from '../support/data'
import { expect, test } from '../support/fixtures'
import { call, unique } from '../support/api'

/** ISO date of the first day of the month `months` before the current one, in the runner's time zone. */
function monthStart(months: number) {
  const now = new Date()
  const d = new Date(now.getFullYear(), now.getMonth() - months, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}

test('a rent payment is booked in the account, and the arrears show on the tenancy, the list and the start page', async ({ page, api }) => {
  // Rent of 840,00 € (650 + 150 + 40) for this month and the two before it.
  const home = await longTermUnit(api)
  const start = monthStart(2)
  const rented = await tenancy(api, home.unitId, start, 'Erika Musterfrau')
  const entity = await owner(api)
  const giro = await account(api, entity.id, unique('Giro '))

  await page.goto(`/dauervermietung/mietverhaeltnisse/${rented.id}`)
  const rents = page.getByRole('region', { name: 'Mietkonto' })
  await expect(rents.getByLabel('Stand des Mietkontos')).toContainText('2.520,00 €')
  await rents.getByRole('button', { name: 'Zahlung erfassen' }).click()
  const dialog = page.getByRole('dialog', { name: 'Zahlung erfassen' })
  await dialog.getByRole('textbox', { name: 'Datum' }).fill(germanDate(start))
  await dialog.getByLabel('Betrag (€)').fill('840,00')
  await dialog.getByRole('combobox', { name: 'Konto' }).click()
  await page.getByRole('option', { name: new RegExp(giro.name) }).click()
  await dialog.getByRole('button', { name: 'Speichern' }).click()
  await expect(dialog).toBeHidden()

  // The first month is paid; the second is open and overdue.
  await expect(rents.getByLabel('Stand des Mietkontos')).toContainText('Rückstand1.680,00 €überfällig')
  await expect(rents.getByRole('table', { name: 'Zahlungen' })).toContainText(giro.name)

  // The account has the transaction; it is changed only through the payment.
  await page.goto(`/finanzen/konten/${giro.id}`)
  const row = page.getByRole('row', { name: /Erika Musterfrau/ })
  await expect(row).toContainText('840,00')
  await expect(row.getByRole('link', { name: 'Mietzahlung' })).toBeVisible()
  await expect(row.getByRole('button', { name: 'Transaktion bearbeiten' })).toHaveCount(0)

  // The list shows the arrears.
  await page.goto('/dauervermietung/mietverhaeltnisse')
  await expect(page.getByRole('row', { name: /Erika Musterfrau/ })).toContainText('1.680,00 € Rückstand')

  // The start page tile lists the tenancy.
  await call(api, 'PUT', '/api/dashboard', { tiles: [{ id: 'arrears', type: 'longtermrental.arrears', settings: {} }] })
  await page.goto('/')
  const tile = page.getByRole('region', { name: 'Mietrückstände' })
  await expect(tile.getByRole('link', { name: /Erika Musterfrau/ })).toContainText('1.680,00 €')
})
