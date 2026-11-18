import { expect, test } from '../support/fixtures'
import { connectMailbox, deliver, fewoConfirmation } from '../support/mail'

test('fetch older mails with a quick range, see the result and filter open mails', async ({ page, api, user }) => {
  const address = `${user.username}@example.org`
  // A cut-off date in the future: the regular poll finds nothing, only the fetch of older mails does.
  const tomorrow = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10)
  await connectMailbox(api, address, tomorrow)
  const ref = String(Date.now()).slice(-6)
  await deliver(address, fewoConfirmation({ to: address, object: 'Haus Unbekannt', ref, arrival: '2030-05-01', departure: '2030-05-04', guest: 'Erika Musterfrau' }))
  await deliver(
    address,
    `From: news@example.org\nTo: ${address}\nSubject: Rundbrief ${ref}\nMessage-ID: <rundbrief-${ref}@example.org>\n\nHallo`,
  )

  await page.goto('/posteingang')
  await page.getByRole('radio', { name: '1 Jahr' }).click()
  await page.getByRole('button', { name: 'Ältere Mails abrufen' }).click()
  await expect(page.getByText('Abruf abgeschlossen: 2 neue Mails')).toBeVisible({ timeout: 30_000 })
  const booking = page.getByRole('row', { name: /^Mail Neue Buchung für Haus Unbekannt/ })
  const newsletter = page.getByRole('row', { name: `Mail Rundbrief ${ref}` })
  await expect(booking).toBeVisible()
  await expect(newsletter).toBeVisible()

  // Fetching the same period again adds nothing.
  await page.getByRole('button', { name: 'Ältere Mails abrufen' }).click()
  await expect(page.getByText('Abruf abgeschlossen: 0 neue Mails')).toBeVisible({ timeout: 30_000 })

  await newsletter.click()
  await page.getByRole('dialog').getByRole('button', { name: 'Ignorieren' }).click()
  await page.keyboard.press('Escape')

  await page.getByRole('button', { name: 'Offen' }).click()
  await expect(booking).toBeVisible()
  await expect(newsletter).toBeHidden()
  await page.reload()
  await expect(page.getByRole('button', { name: 'Offen' })).toHaveAttribute('aria-pressed', 'true')
  await expect(booking).toBeVisible()
  await expect(newsletter).toBeHidden()
})
