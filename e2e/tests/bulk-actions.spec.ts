import { expect, test } from '../support/fixtures'
import { call } from '../support/api'
import { booking, holidayHome } from '../support/data'

test('cancel several bookings at once and fill a statement with several bookings', async ({ page, api }) => {
  const home = await holidayHome(api)
  // Fixed dates in one month far ahead, so the test does not depend on today.
  await booking(api, home.unitId, '2030-03-02', '2030-03-05', 'Ida Testgast', 'E2E-B1', '300.00')
  await booking(api, home.unitId, '2030-03-10', '2030-03-12', 'Theo Testgast', 'E2E-B2', '200.00')
  await booking(api, home.unitId, '2030-04-10', '2030-04-12', 'Nora Testgast', 'E2E-B3', '180.00')
  await booking(api, home.unitId, '2030-04-20', '2030-04-22', 'Leon Testgast', 'E2E-B4', '160.00')

  // Cancel the two April bookings together.
  await page.goto(`/ferienvermietung/buchungen?unitId=${home.unitId}&from=2030-04-01&to=2030-04-30`)
  await page.getByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }).click()
  const bar = page.getByRole('toolbar', { name: 'Aktionen für die Auswahl' })
  await expect(bar.getByText('2 ausgewählt')).toBeVisible()
  await bar.getByRole('button', { name: 'Stornieren' }).click()
  await expect(page.getByText('2 storniert')).toBeVisible()

  // A March statement gets both March bookings, preselected by its period.
  const statement = await call<{ id: number }>(api, 'POST', '/api/vacationrental/statements', {
    unitId: home.unitId,
    provider: 'Fewo & Meer',
    periodFrom: '2030-03-01',
    periodTo: '2030-03-31',
    statementDate: '2030-04-05',
  })
  await page.goto(`/ferienvermietung/abrechnungen/${statement.id}`)
  await page.getByRole('button', { name: 'Buchungen zuordnen' }).click()
  const dialog = page.getByRole('dialog', { name: 'Buchungen zuordnen' })
  await dialog.getByLabel('Provision (%)').fill('10')
  await dialog.getByLabel('Reinigung je Buchung (€)').fill('50')
  await dialog.getByRole('button', { name: '2 zuordnen' }).click()
  await expect(page.getByText('2 zugeordnet')).toBeVisible()
  // (300 - 30 - 50) + (200 - 20 - 50) = 350
  await expect(page.getByTestId('payout')).toContainText('350,00')
})
