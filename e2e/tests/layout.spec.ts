import type { Page } from '@playwright/test'
import { call } from '../support/api'
import { account, booking, holidayHome, owner } from '../support/data'
import { expect, test } from '../support/fixtures'
import { layoutProblems } from '../support/layout'
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
