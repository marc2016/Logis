import { readFileSync } from 'node:fs'
import { expect, test } from '../support/fixtures'
import { booking, holidayHome } from '../support/data'
import { connectMailbox, deliver, fetchMails, fewoStatement } from '../support/mail'

/** Built from invented rows by the backend's StatementPdfs (checked by FewoStatementPdfTest). */
const PDF = readFileSync(new URL('../fixtures/statement-fewo.pdf', import.meta.url))
const OBJECT = 'Haus Düne - Beispielort'

test('a statement mail becomes a statement with its PDF, and its open line is assigned by hand', async ({ page, api, user }) => {
  const home = await holidayHome(api)
  // The PDF lists Erika from 05.03. to 12.03.2027 and Max from 20.03. to 22.03.2027.
  await booking(api, home.unitId, '2027-03-05', '2027-03-12', 'Erika Musterfrau', undefined, '790.00')
  await booking(api, home.unitId, '2027-03-20', '2027-03-23', 'Max Mustermann')
  const address = `${user.username}@example.org`
  await connectMailbox(api, address)
  await deliver(address, fewoStatement({ to: address, month: 'März 2027', object: OBJECT, pdf: PDF, fileName: 'Abrechnung März 2027.pdf' }))
  await fetchMails(api, /Ihre Abrechnung/, 1)

  // The object is not known yet: assign it to the unit once.
  await page.goto('/posteingang')
  const mailRow = page.getByRole('row', { name: /Mail Ihre Abrechnung für März 2027/ })
  await mailRow.click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox', { name: /Einheit für/ }).click()
  await page.getByRole('option', { name: home.name }).click()
  await dialog.getByRole('button', { name: 'Zuordnen' }).click()
  await expect(mailRow.getByRole('cell', { name: 'Abrechnung angelegt' })).toBeVisible()
  // The open dialog shows the mail as it was; opened again, it links to the statement.
  await page.keyboard.press('Escape')
  await mailRow.click()
  await page.getByRole('dialog').getByRole('button', { name: 'Zur Abrechnung' }).click()

  // Erika's row is settled; Max's period differs from his booking and waits as an open line.
  await expect(page).toHaveURL(/\/ferienvermietung\/abrechnungen\/\d+$/)
  await expect(page.getByRole('heading', { name: 'Fewo & Meer' })).toBeVisible()
  await expect(page.getByLabel('Wichtige Angaben')).toContainText('Re-Nr. E2E-2027-03')
  await expect(page.getByRole('status', { name: 'Zu prüfen' })).toContainText('1 offene Zeile')
  await expect(page.getByTestId('payout')).toHaveText('665,00 €')
  const open = page.getByRole('region', { name: 'Offene Zeilen' })
  await open.getByRole('button', { name: 'Max Mustermann zuordnen' }).click()
  const assign = page.getByRole('dialog', { name: 'Zeile zuordnen' })
  await assign.getByRole('combobox', { name: 'Buchung' }).click()
  await page.getByRole('option', { name: /20\.03\.2027 – 23\.03\.2027/ }).click()
  await assign.getByRole('button', { name: 'Zuordnen' }).click()

  // Now the payout equals the balance of the PDF and nothing is left to review.
  await expect(page.getByTestId('payout')).toHaveText('927,51 €')
  await expect(page.getByRole('status', { name: 'Zu prüfen' })).toBeHidden()
  await expect(open).toBeHidden()

  // The PDF is one of the statement's documents and opens.
  const documents = page.getByRole('region', { name: 'Dokumente' })
  const file = documents.getByRole('link', { name: /Abrechnung März 2027\.pdf/ })
  await expect(file).toBeVisible()
  const response = await api.get((await file.getAttribute('href'))!)
  expect(response.headers()['content-type']).toContain('application/pdf')

  // The list no longer marks it.
  await page.goto('/ferienvermietung/abrechnungen')
  const row = page.getByRole('row', { name: /Abrechnung Fewo & Meer/ })
  await expect(row).toBeVisible()
  await expect(row.getByText('zu prüfen')).toBeHidden()
})
