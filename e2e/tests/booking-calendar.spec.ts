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

test('the calendar fits the screen and shows weekdays and today', async ({ page, api }, testInfo) => {
  await holidayHome(api)
  await page.goto('/ferienvermietung/kalender')
  const grid = page.getByRole('grid', { name: 'Belegung' })
  const today = page.locator('[data-today="true"]')
  await expect(today).toHaveCount(1)
  // No sideways scrolling: nothing sticks out of the calendar, and day 31 is on the screen (spec occupancy).
  const frame = page.getByTestId('calendar-frame')
  expect(await frame.evaluate((el) => el.scrollWidth <= el.clientWidth)).toBe(true)
  const year = new Date().getFullYear()
  await expect(page.getByRole('row', { name: `Januar ${year}` }).getByRole('gridcell', { name: new RegExp(`^\\p{L}+, 31\\. Januar ${year}`, 'u') })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)
  await expect(grid).toBeVisible()

  // Today as the browser sees it: its time zone can be a day ahead of the test runner's around midnight.
  const now = await page.evaluate(() => ({ day: new Date().getDate(), weekday: new Date().getDay() }))
  const weekday = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'][now.weekday]
  // Wide calendars show the weekday under the number; on the phone only the number is left.
  const shown = await today.evaluate((el) => (el as HTMLElement).innerText.replace(/\s+/g, ''))
  expect(shown).toBe(testInfo.project.name === 'phone' ? `${now.day}` : `${now.day}${weekday}`)
  await expect(today).toHaveAttribute('aria-label', /, heute$/)
  await expect(today).toHaveAttribute('aria-current', 'date')
})
