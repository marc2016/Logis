import { expect, test } from '../support/fixtures'
import { call } from '../support/api'
import { holidayHome, inDays, germanDate } from '../support/data'

test('record a booking, see it in the calendar and settle it', async ({ page, api }) => {
  const home = await holidayHome(api)
  const arrival = inDays(5)
  const departure = inDays(8)
  const month = arrival.slice(0, 7)
  // The agency list is shared and outlives each run, hence the unique name.
  const agency = await call<{ id: number; name: string }>(api, 'POST', '/api/vacationrental/agencies', {
    name: `E2E Vermittler ${Date.now()}`,
  })

  await page.goto(`/ferienvermietung/kalender?year=${month.slice(0, 4)}`)
  await expect(page.getByRole('combobox', { name: 'Einheit' })).toContainText(home.name)
  await page.getByRole('button', { name: 'Buchung' }).click()
  const dialog = page.getByRole('dialog', { name: 'Buchung erfassen' })
  await expect(dialog.getByRole('combobox', { name: 'Einheit' })).toContainText(home.name)
  await dialog.getByRole('textbox', { name: 'Anreise' }).fill(germanDate(arrival))
  await dialog.getByRole('textbox', { name: 'Abreise' }).fill(germanDate(departure))
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

  // An existing booking opens as its own page; the back link returns to the calendar.
  await row.getByRole('button', { name: 'Max Testgast, Geplant' }).click()
  await expect(page).toHaveURL(/\/ferienvermietung\/buchungen\/\d+$/)
  await expect(page.getByRole('heading', { name: 'Max Testgast' })).toBeVisible()
  await expect(page.getByLabel('Wichtige Angaben')).toContainText(agency.name)
  await page.getByRole('link', { name: 'Zurück zum Kalender' }).click()
  await expect(page).toHaveURL(new RegExp(`/ferienvermietung/kalender\\?year=${y}`))

  // Statement through the API, the booking is assigned in the UI.
  const statement = await call<{ id: number }>(api, 'POST', '/api/vacationrental/statements', {
    unitId: home.unitId,
    provider: 'Fewo & Meer',
    periodFrom: `${month}-01`,
    periodTo: departure,
    statementDate: departure,
  })
  // An old link that selected the statement in the list opens the statement's own page.
  await page.goto(`/ferienvermietung/abrechnungen?statement=${statement.id}`)
  await expect(page).toHaveURL(new RegExp(`/ferienvermietung/abrechnungen/${statement.id}$`))
  await expect(page.getByRole('heading', { name: 'Fewo & Meer' })).toBeVisible()
  await page.getByRole('button', { name: 'Buchung zuordnen' }).click()
  const line = page.getByRole('dialog', { name: 'Buchung zuordnen' })
  await line.getByRole('combobox', { name: 'Buchung' }).click()
  await page.getByRole('option', { name: /Max Testgast/ }).click()
  await line.getByLabel('Brutto (€)').fill('360')
  await line.getByRole('button', { name: 'Speichern' }).click()
  await expect(line).toBeHidden()
  await expect(page.getByRole('cell', { name: 'Max Testgast', exact: true })).toBeVisible()

  await page.goto(`/ferienvermietung/kalender?year=${month.slice(0, 4)}`)
  await expect(page.getByRole('row', { name: monthName }).getByRole('button', { name: 'Max Testgast, Abgerechnet' })).toBeVisible()
})

test('month labels stay visible when the calendar is scrolled sideways', async ({ page, api }) => {
  const home = await holidayHome(api)
  const year = new Date().getFullYear()
  await page.goto(`/ferienvermietung/kalender?year=${year}`)
  await expect(page.getByRole('combobox', { name: 'Einheit' })).toContainText(home.name)
  const grid = page.getByRole('grid', { name: 'Belegung' })
  // Scroll the calendar to its right end, as on a phone where day 31 is far off screen.
  await grid.locator('..').evaluate((el) => el.scrollTo({ left: el.scrollWidth }))
  const label = page.getByRole('row', { name: `Juni ${year}` }).getByRole('rowheader')
  await expect(label).toBeInViewport()
  const box = (await label.boundingBox())!
  const container = (await grid.locator('..').boundingBox())!
  expect(box.x).toBeLessThanOrEqual(container.x + 1)
})

test('the calendar shows weekdays and today, scrolled into view', async ({ page, api }) => {
  await holidayHome(api)
  await page.goto('/ferienvermietung/kalender')
  const today = page.locator('[data-today="true"]')
  await expect(today).toHaveCount(1)
  // On the phone the calendar is wider than the screen: it opens scrolled sideways to today, next to the month
  // column (80 px), while the page itself stays where it is.
  await expect(async () => {
    const frame = (await page.getByRole('grid', { name: 'Belegung' }).locator('..').boundingBox())!
    const cell = (await today.boundingBox())!
    expect(cell.x).toBeGreaterThanOrEqual(frame.x + 80)
    expect(cell.x + cell.width).toBeLessThanOrEqual(frame.x + frame.width)
  }).toPass()
  expect(await page.evaluate(() => window.scrollY)).toBe(0)
  // Today as the browser sees it: its time zone can be a day ahead of the test runner's around midnight.
  const now = await page.evaluate(() => ({ day: new Date().getDate(), weekday: new Date().getDay() }))
  const weekday = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'][now.weekday]
  await expect(today).toHaveText(`${now.day}${weekday}`)
  await expect(today).toHaveAttribute('aria-label', /, heute$/)
  await expect(today).toHaveAttribute('aria-current', 'date')
})
