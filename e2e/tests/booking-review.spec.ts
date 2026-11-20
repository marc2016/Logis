import { call } from '../support/api'
import { holidayHome } from '../support/data'
import { expect, test } from '../support/fixtures'
import { connectMailbox, deliver, fetchMails, fewoConfirmation, type InboxMail } from '../support/mail'

test('bookings to review are clicked through one after the other', async ({ page, api, user }) => {
  const home = await holidayHome(api)
  const address = `${user.username}@example.org`
  const stamp = String(Date.now()).slice(-5)
  await connectMailbox(api, address)
  const bookingOf = async (mailId: number) =>
    (await call<InboxMail[]>(api, 'GET', '/api/mail/inbox')).find((m) => m.id === mailId)!.linkId!

  // A new booking from a mail, still to be reviewed.
  const first = { to: address, object: home.name, ref: `1${stamp}`, arrival: '2030-09-01', departure: '2030-09-04', guest: 'Ida Testgast' }
  await deliver(address, fewoConfirmation(first))
  const [firstMail] = await fetchMails(api, /Neue Buchung/, 1)
  await call(api, 'POST', `/api/vacationrental/mail/${firstMail.id}/assign`, { unitId: home.unitId })

  // A confirmed booking that a second mail proposes to change.
  const second = { to: address, object: home.name, ref: `2${stamp}`, arrival: '2030-09-10', departure: '2030-09-12', guest: 'Theo Testgast' }
  await deliver(address, fewoConfirmation(second))
  const mails = await fetchMails(api, /Neue Buchung/, 2)
  const secondId = await bookingOf(mails.find((m) => m.id !== firstMail.id)!.id)
  await call(api, 'POST', `/api/vacationrental/bookings/${secondId}/confirm`)
  await deliver(address, fewoConfirmation({ ...second, departure: '2030-09-13', messageId: `fewo-2${stamp}-change` }))
  await expect
    .poll(async () => (await fetchMails(api, /Neue Buchung/, 3)).map((m) => m.result), { timeout: 30_000 })
    .toContain('CHANGE_PROPOSED')

  await page.goto('/ferienvermietung/buchungen')
  await page.getByRole('button', { name: 'Prüfen (2)' }).click()
  const bar = page.getByRole('navigation', { name: 'Prüfen' })
  await expect(bar).toContainText('1 von 2')
  await expect(page.getByRole('heading', { name: 'Ida Testgast' })).toBeVisible()

  // Back and forth without deciding.
  await bar.getByRole('button', { name: 'Weiter' }).click()
  await expect(page.getByRole('heading', { name: 'Theo Testgast' })).toBeVisible()
  await expect(bar).toContainText('2 von 2')
  await bar.getByRole('button', { name: 'Zurück' }).click()
  await expect(page.getByRole('heading', { name: 'Ida Testgast' })).toBeVisible()

  // Each decision opens the next booking.
  await page.getByRole('button', { name: 'Bestätigen' }).click()
  await expect(page.getByRole('heading', { name: 'Theo Testgast' })).toBeVisible()
  await expect(bar).toContainText('1 von 1')
  await expect(page.getByRole('table', { name: 'Vorgeschlagene Änderung' })).toContainText('13.09.2030')
  await page.getByRole('button', { name: 'Übernehmen' }).click()

  await expect(page.getByText('Alles geprüft.')).toBeVisible()
  await page.getByRole('link', { name: 'Zur Liste' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Buchungen' })).toBeVisible()
  await expect(page.getByRole('button', { name: /Prüfen \(/ })).toBeHidden()
})
