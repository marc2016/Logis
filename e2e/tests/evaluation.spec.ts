import { call } from '../support/api'
import { booking, holidayHome, statement } from '../support/data'
import { expect, test } from '../support/fixtures'

test('the evaluation shows nights, occupancy and money of a month', async ({ page, api }) => {
  const home = await holidayHome(api)
  const settled = await booking(api, home.unitId, '2030-03-02', '2030-03-05', 'Ida Testgast', undefined, '300.00')
  await booking(api, home.unitId, '2030-03-10', '2030-03-12', 'Theo Testgast', undefined, '200.00')
  const march = await statement(api, home.unitId, 'Fewo & Meer', '2030-03-01', '2030-03-31')
  await call(api, 'PUT', `/api/vacationrental/statements/${march.id}/lines/${settled.id}`, { gross: '300.00', commission: '30.00', cleaning: '50.00' })

  await page.goto(`/ferienvermietung/auswertung?unitId=${home.unitId}&year=2030`)
  const row = page.getByRole('row', { name: /März/ })
  // 3 + 2 nights of 31 days; settled net 300 - 30 - 50; the other stay is still planned.
  await expect(row.getByRole('cell', { name: '5', exact: true })).toBeVisible()
  await expect(row).toContainText('16 %')
  await expect(row).toContainText('220,00 €')
  await expect(row).toContainText('200,00 €')
})
