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

test('charts of a unit, with the previous year', async ({ page, api }) => {
  const home = await holidayHome(api)
  await booking(api, home.unitId, '2030-07-01', '2030-07-11', 'Ida Testgast', undefined, '900.00')
  await booking(api, home.unitId, '2029-07-05', '2029-07-09', 'Theo Testgast', undefined, '400.00')

  await page.goto(`/ferienvermietung/auswertung?unitId=${home.unitId}&year=2030`)
  const occupancy = page.getByRole('figure', { name: 'Belegung je Monat 2030' })
  await expect(occupancy.locator('.recharts-bar-rectangle').first()).toBeVisible()
  await expect(occupancy).toContainText('Belegung 2030')
  await expect(occupancy).toContainText('Eigenbelegung')
  const money = page.getByRole('figure', { name: 'Einnahmen je Monat 2030' })
  await expect(money).toContainText('Netto abgerechnet 2030')
  await expect(money).toContainText('Mietpreis geplant')

  await page.getByRole('checkbox', { name: 'Vorjahr einblenden' }).click()
  await expect(page).toHaveURL(/previous=1/)
  const withPrevious = page.getByRole('figure', { name: 'Belegung je Monat 2030 mit Vorjahr 2029' })
  await expect(withPrevious).toContainText('Belegung 2029')
  await expect(withPrevious.locator('.recharts-line-curve')).toBeVisible()
  // July 2029: 4 of 31 nights.
  await expect(page.getByRole('row', { name: /Juli/ })).toContainText('13 %')
  await page.reload()
  await expect(page.getByRole('checkbox', { name: 'Vorjahr einblenden' })).toBeChecked()
})

test('all units compared across properties', async ({ page, api }) => {
  const first = await holidayHome(api, 'Erstes Haus')
  const second = await holidayHome(api, 'Zweites Haus')
  await booking(api, first.unitId, '2031-05-01', '2031-05-11', 'Ida Testgast')
  await booking(api, second.unitId, '2031-06-01', '2031-06-05', 'Theo Testgast')

  await page.goto(`/ferienvermietung/auswertung?unitId=${first.unitId}&year=2031`)
  await page.getByRole('combobox', { name: 'Einheit' }).click()
  await page.getByRole('option', { name: 'Alle Einheiten' }).click()
  await expect(page).toHaveURL(/unitId=all/)

  // Long names wrap onto two lines in the chart's axis, so look for the unique part of each name.
  const chart = page.getByRole('figure', { name: 'Belegung je Einheit 2031' })
  await expect(chart).toContainText(first.name.split(' ').at(-1)!)
  await expect(chart).toContainText(second.name.split(' ').at(-1)!)
  await expect(chart.locator('.recharts-bar-rectangle')).toHaveCount(2)
  const table = page.getByRole('table', { name: 'Alle Einheiten 2031' })
  await expect(table.getByRole('row', { name: new RegExp(first.name) })).toContainText('10')
  await expect(table.getByRole('row', { name: new RegExp(second.name) })).toContainText('4')
})
