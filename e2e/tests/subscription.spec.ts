import ICAL from 'ical.js'
import { expect, test } from '../support/fixtures'
import { booking, holidayHome, inDays } from '../support/data'

test('subscribe to the calendar without guest data and revoke the link', async ({ page, api, playwright, baseURL }) => {
  const home = await holidayHome(api)
  await booking(api, home.unitId, inDays(10), inDays(13), 'Erika Testgast', 'E2E-1')

  await page.goto('/ferienvermietung/kalender')
  await page.getByRole('button', { name: 'Abonnieren' }).click()
  const dialog = page.getByRole('dialog', { name: 'Kalender abonnieren' })
  await dialog.getByRole('textbox', { name: 'Name' }).fill('Reinigung')
  await dialog.getByRole('button', { name: 'Link erstellen' }).click()
  const url = (await dialog.getByLabel('Neuer Link').textContent())!.trim()
  expect(url).toMatch(/\/api\/ical\/[A-Za-z0-9_-]{43}\.ics$/)
  await expect(dialog.getByRole('list', { name: 'Meine Links' })).toContainText('ohne Gastdaten')

  // A calendar app has no session.
  const anonymous = await playwright.request.newContext({ baseURL })
  const res = await anonymous.get(url)
  expect(res.status()).toBe(200)
  expect(res.headers()['content-type']).toContain('text/calendar')
  const text = await res.text()
  expect(text).not.toContain('Erika')
  const events = new ICAL.Component(ICAL.parse(text)).getAllSubcomponents('vevent').map((v) => new ICAL.Event(v))
  expect(events.map((e) => [e.summary, e.startDate.toString(), e.endDate.toString()])).toEqual([
    ['Belegt', inDays(10), inDays(14)],
  ])

  await dialog.getByRole('button', { name: 'Reinigung widerrufen' }).click()
  await expect(dialog.getByText('Noch keine Links.')).toBeVisible()
  expect((await anonymous.get(url)).status()).toBe(404)
  await anonymous.dispose()
})
