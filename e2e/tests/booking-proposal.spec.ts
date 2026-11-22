import { call } from '../support/api'
import { holidayHome } from '../support/data'
import { expect, test } from '../support/fixtures'
import { connectMailbox, deliver, fetchMails, fewoCancellation, fewoConfirmation, type InboxMail } from '../support/mail'

test('a change mail proposes a new departure that is applied on the booking page', async ({ page, api, user }) => {
  const home = await holidayHome(api)
  const address = `${user.username}@example.org`
  const ref = String(Date.now()).slice(-6)
  const mail = { to: address, object: home.name, ref, arrival: '2030-08-10', guest: 'Lena Testgast' }
  await connectMailbox(api, address)

  await deliver(address, fewoConfirmation({ ...mail, departure: '2030-08-14' }))
  const [confirmation] = await fetchMails(api, /Neue Buchung/, 1)
  await call(api, 'POST', `/api/vacationrental/mail/${confirmation.id}/assign`, { unitId: home.unitId })
  const inbox = await call<InboxMail[]>(api, 'GET', '/api/mail/inbox')
  const bookingId = inbox.find((m) => m.id === confirmation.id)!.linkId!
  await call(api, 'POST', `/api/vacationrental/bookings/${bookingId}/confirm`)

  // The agency sends the same booking again with a later departure.
  await deliver(address, fewoConfirmation({ ...mail, departure: '2030-08-15', messageId: `fewo-${ref}-change` }))
  await expect
    .poll(async () => (await fetchMails(api, /Neue Buchung/, 2)).map((m) => m.result), { timeout: 30_000 })
    .toContain('CHANGE_PROPOSED')

  await page.goto(`/ferienvermietung/buchungen/${bookingId}`)
  const proposal = page.getByRole('table', { name: 'Vorgeschlagene Änderung' })
  await expect(proposal).toContainText('14.08.2030')
  await proposal.getByRole('button', { name: '15.08.2030' }).click()
  // The new value is marked in the change mail (on the phone, the source tab opens).
  await expect(page.locator('mark[data-active="true"]')).toHaveText('15.08.2030')

  await page.getByRole('button', { name: 'Übernehmen' }).click()
  await expect(proposal).toBeHidden()
  const dataTab = page.getByRole('tab', { name: 'Daten' })
  if (await dataTab.isVisible()) await dataTab.click()
  await expect(page.getByRole('textbox', { name: 'Abreise' })).toHaveValue('15.08.2030')
})

test('a cancellation mail for a booking that is not confirmed yet is reported and applied', async ({ page, api, user }) => {
  const home = await holidayHome(api)
  const address = `${user.username}@example.org`
  const ref = String(Date.now()).slice(-6)
  const mail = { to: address, object: home.name, ref, arrival: '2030-10-05', departure: '2030-10-09' }
  await connectMailbox(api, address)

  await deliver(address, fewoConfirmation({ ...mail, guest: 'Nora Testgast' }))
  const [confirmation] = await fetchMails(api, /Neue Buchung/, 1)
  await call(api, 'POST', `/api/vacationrental/mail/${confirmation.id}/assign`, { unitId: home.unitId })
  const inbox = await call<InboxMail[]>(api, 'GET', '/api/mail/inbox')
  const bookingId = inbox.find((m) => m.id === confirmation.id)!.linkId!

  // The agency cancels the booking before the owner has confirmed it.
  await deliver(address, fewoCancellation(mail))
  await expect
    .poll(async () => (await fetchMails(api, /Stornierung/, 1)).map((m) => m.result), { timeout: 30_000 })
    .toContain('CHANGE_PROPOSED')

  await page.goto('/ferienvermietung/buchungen')
  const row = page.getByRole('row', { name: new RegExp(`Buchung ${ref}`) })
  await expect(row).toContainText('zu prüfen')
  await expect(row).toContainText('Stornierung gemeldet')

  await page.goto(`/ferienvermietung/buchungen/${bookingId}`)
  await expect(page.getByText('Stornierung per E-Mail – bitte prüfen')).toBeVisible()
  await expect(page.getByText('Die Buchung ist noch nicht bestätigt.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Bestätigen' })).toHaveCount(0)
  // The cancellation mail is the source that is shown (on the phone in the source tab).
  const sourceTab = page.getByRole('tab', { name: 'Quelle' })
  if (await sourceTab.isVisible()) await sourceTab.click()
  await expect(page.getByText(`Ihre Buchung Nr. ${ref}`)).toBeVisible()

  await page.getByRole('button', { name: 'Stornierung übernehmen' }).click()
  await expect(page.getByText('Stornierung per E-Mail – bitte prüfen')).toBeHidden()
  await expect(page.getByText('Stornierung gemeldet')).toBeHidden()
  await expect(page.getByText('zu prüfen')).toBeHidden()
  const dataTab = page.getByRole('tab', { name: 'Daten' })
  if (await dataTab.isVisible()) await dataTab.click()
  await expect(page.getByRole('button', { name: 'Stornierung aufheben' })).toBeVisible()
})
