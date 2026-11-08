import { expect, test } from '../support/fixtures'
import { holidayHome, inDays } from '../support/data'
import { deliver, fewoConfirmation, IMAP_HOST, IMAP_PORT } from '../support/mail'

test('import a booking from a forwarded agency mail and confirm it', async ({ page, api, user }) => {
  const home = await holidayHome(api)
  const address = `${user.username}@example.org`
  const ref = String(Date.now()).slice(-6)
  const arrival = inDays(20)
  const departure = inDays(23)
  await deliver(address, fewoConfirmation({ to: address, object: home.name, ref, arrival, departure, guest: 'Lena Testgast' }))

  await page.goto('/posteingang')
  await page.getByLabel('IMAP-Server').fill(IMAP_HOST)
  await page.getByLabel('Port').fill(String(IMAP_PORT))
  await page.getByLabel('Benutzername').fill(address)
  await page.getByLabel('Passwort').fill('beliebig')
  await page.getByRole('checkbox', { name: 'Verschlüsselt (TLS)' }).uncheck()
  await page.getByRole('button', { name: 'Speichern' }).click()
  await page.getByRole('button', { name: 'Jetzt abrufen' }).click()

  // Fetching runs in the background.
  const mailRow = page.getByRole('row', { name: new RegExp(`Mail Neue Buchung für ${home.name}`) })
  await expect(async () => {
    await page.reload()
    await expect(mailRow).toBeVisible({ timeout: 2_000 })
  }).toPass({ timeout: 30_000 })

  // The agency's property name is not known yet: assign it to the unit once.
  await mailRow.click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox', { name: /Einheit für/ }).click()
  await page.getByRole('option', { name: home.name }).click()
  await dialog.getByRole('button', { name: 'Zuordnen' }).click()
  await expect(mailRow.getByRole('cell', { name: 'Buchung angelegt' })).toBeVisible()

  // The imported booking waits for confirmation.
  await page.goto(`/ferienvermietung/buchungen?q=${ref}`)
  const bookingRow = page.getByRole('row', { name: new RegExp(`Buchung ${ref}`) })
  await bookingRow.click()
  const booking = page.getByRole('dialog')
  await expect(booking.getByText('zu prüfen')).toBeVisible()
  await expect(booking.getByRole('textbox', { name: 'Name' })).toHaveValue('Lena Testgast')
  await booking.getByRole('button', { name: 'Bestätigen' }).click()
  await expect(booking.getByText('zu prüfen')).toBeHidden()
  await page.keyboard.press('Escape')

  await page.goto(`/ferienvermietung/kalender?year=${arrival.slice(0, 4)}`)
  await expect(page.getByRole('button', { name: 'Lena Testgast, Geplant' })).toBeVisible()
})
