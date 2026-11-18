import { expect, test } from '../support/fixtures'
import { holidayHome, inDays } from '../support/data'
import { connectMailbox, deliver, fetchMails, fewoOwnerBlock } from '../support/mail'

test('an owner block mail creates an owner use that blocks the calendar', async ({ page, api, user }) => {
  const home = await holidayHome(api)
  const address = `${user.username}@example.org`
  await connectMailbox(api, address)
  const ref = `E${String(Date.now()).slice(-6)}`
  const arrival = inDays(30)
  const departure = inDays(35)
  await deliver(address, fewoOwnerBlock({ to: address, object: home.name, ref, arrival, departure }))
  await fetchMails(api, /Eigentümerblockung/, 1)

  // The agency's property name is not known yet: assign it to the unit once.
  await page.goto('/posteingang')
  const mailRow = page.getByRole('row', { name: new RegExp(`Mail Eigentümerblockung ${ref}`) })
  await mailRow.click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox', { name: /Einheit für/ }).click()
  await page.getByRole('option', { name: home.name }).click()
  await dialog.getByRole('button', { name: 'Zuordnen' }).click()
  await expect(mailRow.getByRole('cell', { name: 'Eigenbelegung angelegt' })).toBeVisible()
  await page.keyboard.press('Escape')

  // The owner use appears in the calendar, set apart from guest stays.
  const [y, m] = arrival.split('-').map(Number)
  const monthName = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric' }).format(new Date(y, m - 1, 1))
  await page.goto(`/ferienvermietung/kalender?year=${y}`)
  await expect(page.getByRole('combobox', { name: 'Einheit' })).toContainText(home.name)
  await expect(page.getByRole('row', { name: monthName }).getByRole('button', { name: 'Eigenbelegung' })).toBeVisible()

  // A guest booking in that period is rejected.
  await page.getByRole('button', { name: 'Buchung' }).click()
  const form = page.getByRole('dialog', { name: 'Buchung erfassen' })
  await expect(form.getByRole('combobox', { name: 'Einheit' })).toContainText(home.name)
  await form.getByLabel('Anreise').fill(inDays(31))
  await form.getByLabel('Abreise').fill(inDays(33))
  await form.getByRole('button', { name: 'Speichern' }).click()
  await expect(form.getByText('Die Einheit ist in diesem Zeitraum bereits belegt')).toBeVisible()
})
