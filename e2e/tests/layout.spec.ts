import type { Page } from '@playwright/test'
import { call } from '../support/api'
import { account, booking, holidayHome, inDays, longTermUnit, owner, tenancy } from '../support/data'
import { adminPage, expect, test } from '../support/fixtures'
import { layoutProblems } from '../support/layout'
import { APP_TOKEN, userKey } from '../support/pushover'
import { connectMailbox, deliver, fetchMails, fewoConfirmation, type InboxMail } from '../support/mail'

/** Phone, tablet and tablet landscape; from 768 px the sidebar is open next to the content. */
const WIDTHS = [390, 820, 1024]

async function check(page: Page, path: string, heading: string | RegExp, root?: string) {
  const problems: string[] = []
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
    await page.waitForLoadState('networkidle')
    for (const p of await layoutProblems(page, root)) problems.push(`${path} at ${width} px: ${p}`)
  }
  return problems
}

test('list pages fit on phone and tablet screens', async ({ page, api }) => {
  const home = await holidayHome(api)
  await booking(api, home.unitId, '2030-07-01', '2030-07-08', 'Erika Musterfrau', 'L-1001')
  const entity = await owner(api)
  const giro = await account(api, entity.id)
  await call(api, 'POST', '/api/transactions', { accountId: giro.id, bookingDate: '2030-02-03', amount: '-12.50', counterparty: 'Beispielmarkt' })

  const problems = [
    ...(await check(page, '/ferienvermietung/buchungen', 'Buchungen')),
    ...(await check(page, '/finanzen/transaktionen', 'Transaktionen')),
    ...(await check(page, '/posteingang', 'Posteingang')),
    ...(await check(page, '/ferienvermietung/kalender', 'Kalender')),
  ]
  expect(problems).toEqual([])
})

test('the occupancy calendar fits without scrolling sideways', async ({ page, api }) => {
  const home = await holidayHome(api)
  await booking(api, home.unitId, '2030-07-10', '2030-07-12', 'Erika Musterfrau', 'L-3001')
  const problems: string[] = []
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto(`/ferienvermietung/kalender?year=2030`)
    await expect(page.getByRole('row', { name: 'Juli 2030' }).getByRole('button', { name: /Erika Musterfrau/ })).toBeVisible()
    const sizes = await page.getByTestId('calendar-frame').evaluate((el) => ({ scroll: el.scrollWidth, client: el.clientWidth }))
    if (sizes.scroll > sizes.client) problems.push(`calendar at ${width} px: ${sizes.scroll} px content in ${sizes.client} px`)
    const last = page.getByRole('gridcell', { name: /31\. Juli 2030/ })
    const box = (await last.boundingBox())!
    if (box.x + box.width > width + 1) problems.push(`calendar at ${width} px: 31 July ends at ${Math.round(box.x + box.width)} px`)
    for (const p of await layoutProblems(page)) problems.push(`calendar at ${width} px: ${p}`)
  }
  expect(problems).toEqual([])
})

test('the evaluation fits on phone and tablet screens', async ({ page, api }) => {
  const home = await holidayHome(api)
  await booking(api, home.unitId, '2030-07-01', '2030-07-11', 'Erika Musterfrau', 'L-2001')
  const problems = [
    ...(await check(page, `/ferienvermietung/auswertung?unitId=${home.unitId}&year=2030&previous=1`, 'Auswertung')),
    ...(await check(page, '/ferienvermietung/auswertung?unitId=all&year=2030', 'Auswertung')),
  ]
  expect(problems).toEqual([])
})

test('the start page fits on phone and tablet screens', async ({ page, api }) => {
  const home = await holidayHome(api)
  await booking(api, home.unitId, '2030-07-01', '2030-07-11', 'Erika Musterfrau', 'L-3001')
  const entity = await owner(api)
  await account(api, entity.id)
  expect(await check(page, '/', 'Übersicht')).toEqual([])
})

test('the sharing dialog fits on phone and tablet screens', async ({ page, api }) => {
  const home = await holidayHome(api)
  const problems: string[] = []
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto(`/immobilien/${home.propertyId}`)
    await page.getByRole('button', { name: 'Freigeben' }).click()
    await expect(page.getByRole('dialog').getByRole('button', { name: 'Einladen' })).toBeVisible()
    for (const p of await layoutProblems(page, '[role=dialog]')) problems.push(`sharing dialog at ${width} px: ${p}`)
  }
  expect(problems).toEqual([])
})

test('the booking dialog fits on phone and tablet screens', async ({ page, api }) => {
  await holidayHome(api)
  const problems: string[] = []
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/ferienvermietung/buchungen')
    await page.getByRole('button', { name: 'Buchung' }).click()
    const dialog = page.getByRole('dialog', { name: 'Buchung erfassen' })
    await expect(dialog.getByRole('textbox', { name: 'Anreise' })).toHaveAttribute('placeholder', 'TT.MM.JJJJ')
    for (const p of await layoutProblems(page, '[role=dialog]')) problems.push(`booking dialog at ${width} px: ${p}`)
    await page.keyboard.press('Escape')
  }
  expect(problems).toEqual([])
})

test('the review mode fits on phone and tablet screens', async ({ page, api }) => {
  const home = await holidayHome(api)
  const stay = await booking(api, home.unitId, '2030-07-01', '2030-07-08', 'Erika Musterfrau', 'L-4001')
  expect(await check(page, `/ferienvermietung/buchungen/${stay.id}?pruefen=1`, 'Erika Musterfrau')).toEqual([])
})

test('the compact booking view fits on phone and tablet screens', async ({ page, api }) => {
  const home = await holidayHome(api)
  const stay = await booking(api, home.unitId, '2030-07-01', '2030-07-08', 'Erika Musterfrau', 'L-5001')
  await call(api, 'PUT', '/api/preferences/vacationrental.bookingView', 'compact')
  expect(await check(page, `/ferienvermietung/buchungen/${stay.id}`, 'Erika Musterfrau')).toEqual([])
})

test('a booking imported from a mail fits on phone and tablet screens', async ({ page, api, user }) => {
  const home = await holidayHome(api)
  const address = `${user.username}@example.org`
  await connectMailbox(api, address)
  await deliver(address, fewoConfirmation({ to: address, object: home.name, ref: String(Date.now()).slice(-6), arrival: '2030-09-01', departure: '2030-09-05', guest: 'Max Mustermann' }))
  const [mail] = await fetchMails(api, /Neue Buchung/, 1)
  await call(api, 'POST', `/api/vacationrental/mail/${mail.id}/assign`, { unitId: home.unitId })
  const inbox = await call<InboxMail[]>(api, 'GET', '/api/mail/inbox')
  const bookingId = inbox.find((m) => m.id === mail.id)!.linkId!

  expect(await check(page, `/ferienvermietung/buchungen/${bookingId}`, 'Max Mustermann')).toEqual([])
})

test('notification settings fit on phone and tablet screens', async ({ page, api, browser, baseURL }) => {
  // With Pushover set up the account page shows the key form and both columns of switches.
  const admin = await adminPage(browser, baseURL!)
  await call(admin.context().request, 'PUT', '/api/admin/pushover', { token: APP_TOKEN })
  // The page itself: an admin's navigation is longer than the window and scrolls, which the check does not follow.
  const problems = [...(await check(admin, '/einstellungen/pushover', 'Pushover', 'main'))]
  await admin.context().close()
  await call(api, 'PUT', '/api/account/pushover', { userKey: userKey() })

  problems.push(...(await check(page, '/konto', 'Mein Konto')))
  await expect(page.getByRole('checkbox', { name: 'Neue Anmeldung: Pushover' })).toBeEnabled()
  expect(problems).toEqual([])
})

test('long-term rental fits on phone and tablet screens', async ({ page, api }) => {
  const rented = await longTermUnit(api)
  const vacant = await longTermUnit(api)
  const running = await tenancy(api, rented.unitId, inDays(-400), 'Erika Musterfrau')
  await call(api, 'POST', `/api/longtermrental/tenancies/${running.id}/rent-terms`, { validFrom: inDays(-30), coldRent: '680.00', utilitiesAdvance: '160.00', others: [{ label: 'Stellplatz', amount: '40.00' }] })
  await tenancy(api, vacant.unitId, inDays(-400), 'Max Mustermann', inDays(-20))
  await tenancy(api, vacant.unitId, inDays(30), 'Max Mustermann')

  const problems = [
    ...(await check(page, '/dauervermietung/mietverhaeltnisse?state=all', 'Mietverhältnisse')),
    ...(await check(page, '/dauervermietung/einheiten', 'Einheiten')),
    ...(await check(page, `/dauervermietung/mietverhaeltnisse/${running.id}`, 'Erika Musterfrau')),
  ]
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto(`/dauervermietung/mietverhaeltnisse?neu=${rented.unitId}`)
    const dialog = page.getByRole('dialog', { name: 'Mietverhältnis anlegen' })
    await expect(dialog.getByRole('textbox', { name: 'Beginn' })).toBeVisible()
    await dialog.getByRole('button', { name: 'Weiterer Posten' }).click()
    for (const p of await layoutProblems(page, '[role=dialog]')) problems.push(`tenancy dialog at ${width} px: ${p}`)
  }
  expect(problems).toEqual([])
})
