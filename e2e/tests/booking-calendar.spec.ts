import { expect, test } from '../support/fixtures'
import { call } from '../support/api'
import { holidayHome, inDays } from '../support/data'

test('record a booking, see it in the calendar and settle it', async ({ page, api }) => {
  const home = await holidayHome(api)
  const arrival = inDays(5)
  const departure = inDays(8)
  const month = arrival.slice(0, 7)
  // The agency list is shared and outlives each run, hence the unique name.
  const agency = await call<{ id: number; name: string }>(api, 'POST', '/api/vacationrental/agencies', {
    name: `E2E Vermittler ${Date.now()}`,
  })

  await page.goto(`/ferienvermietung/kalender?month=${month}`)
  await expect(page.getByRole('combobox', { name: 'Einheit' })).toContainText(home.name)
  await page.getByRole('button', { name: 'Buchung' }).click()
  const dialog = page.getByRole('dialog', { name: 'Buchung erfassen' })
  await expect(dialog.getByRole('combobox', { name: 'Einheit' })).toContainText(home.name)
  await dialog.getByLabel('Anreise').fill(arrival)
  await dialog.getByLabel('Abreise').fill(departure)
  await dialog.getByLabel('Buchungsnummer').fill('E2E-42')
  await dialog.getByRole('combobox', { name: /Vermittler/ }).click()
  await page.getByPlaceholder('Vermittler suchen oder anlegen').fill(agency.name)
  await page.getByRole('option', { name: agency.name, exact: true }).click()
  await expect(dialog.getByRole('combobox', { name: /Vermittler/ })).toContainText(agency.name)
  await dialog.getByRole('textbox', { name: 'Name' }).fill('Max Testgast')
  await dialog.getByRole('button', { name: 'Speichern' }).click()
  await expect(dialog).toBeHidden()

  const [y, m] = month.split('-').map(Number)
  const monthName = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric' }).format(new Date(y, m - 1, 1))
  const row = page.getByRole('row', { name: monthName })
  await expect(row.getByRole('button', { name: 'Max Testgast, Geplant' })).toBeVisible()

  // Statement through the API, the booking is assigned in the UI.
  const statement = await call<{ id: number }>(api, 'POST', '/api/vacationrental/statements', {
    unitId: home.unitId,
    provider: 'Fewo & Meer',
    periodFrom: `${month}-01`,
    periodTo: departure,
    statementDate: departure,
  })
  await page.goto(`/ferienvermietung/abrechnungen?statement=${statement.id}`)
  await page.getByRole('button', { name: 'Buchung zuordnen' }).click()
  const line = page.getByRole('dialog', { name: 'Buchung zuordnen' })
  await line.getByRole('combobox', { name: 'Buchung' }).click()
  await page.getByRole('option', { name: /Max Testgast/ }).click()
  await line.getByLabel('Brutto (€)').fill('360')
  await line.getByRole('button', { name: 'Speichern' }).click()
  await expect(line).toBeHidden()
  await expect(page.getByRole('cell', { name: 'Max Testgast' })).toBeVisible()

  await page.goto(`/ferienvermietung/kalender?month=${month}`)
  await expect(page.getByRole('row', { name: monthName }).getByRole('button', { name: 'Max Testgast, Abgerechnet' })).toBeVisible()
})
