import { unique } from '../support/api'
import { germanDate, inDays, owner } from '../support/data'
import type { Page } from '@playwright/test'
import { expect, isPhone, openNavigation, test } from '../support/fixtures'

/** Follows an entry of the navigation; on the phone the menu stays open over the page and is closed again. */
async function navigate(page: Page, entry: string) {
  await openNavigation(page)
  await page.getByRole('link', { name: entry, exact: true }).click()
  if (isPhone(page)) await page.keyboard.press('Escape')
}

test('a tenancy from the vacant unit to the rent change, the contract and the vacancy after its end', async ({ page, api }) => {
  // A property in long-term rental.
  const entity = await owner(api)
  const name = unique('Haus ')
  await page.goto('/immobilien')
  await page.getByRole('button', { name: 'Objekt anlegen' }).click()
  const property = page.getByRole('dialog', { name: 'Objekt anlegen' })
  await property.getByRole('combobox', { name: 'Eigentümer' }).click()
  await page.getByRole('option', { name: entity.name }).click()
  await property.getByLabel('Name').fill(name)
  await property.getByLabel('Ort').fill('Talstadt')
  await property.getByRole('combobox', { name: 'Nutzung' }).click()
  await page.getByRole('option', { name: 'Dauermiete' }).click()
  await property.getByRole('button', { name: 'Speichern' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()

  // The module lists it as vacant and leads to a new tenancy for it.
  await navigate(page, 'Einheiten')
  await expect(page.getByRole('heading', { level: 1, name: 'Einheiten' })).toBeVisible()
  const card = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name }) })
  await expect(card.getByText('Leerstand', { exact: true })).toBeVisible()
  await card.getByRole('link', { name: /Mietverhältnis/ }).click()

  const start = inDays(-60)
  const dialog = page.getByRole('dialog', { name: 'Mietverhältnis anlegen' })
  await expect(dialog.getByRole('combobox', { name: 'Einheit' })).toContainText(name)
  await dialog.getByRole('textbox', { name: 'Beginn' }).fill(germanDate(start))
  await dialog.getByRole('group', { name: 'Mieter 1' }).getByLabel('Name').fill('Erika Musterfrau')
  await dialog.getByRole('group', { name: 'Mieter 1' }).getByLabel('E-Mail').fill('erika.musterfrau@example.org')
  await dialog.getByLabel('Kaltmiete (€)').fill('650,00')
  await dialog.getByLabel('Nebenkosten-Vorauszahlung (€)').fill('150,00')
  await dialog.getByLabel('Kaution (€)').fill('1.950,00')
  await dialog.getByRole('button', { name: 'Speichern' }).click()

  // The tenancy opens as its own page.
  await expect(page.getByRole('heading', { level: 1, name: 'Erika Musterfrau' })).toBeVisible()
  const facts = page.getByLabel('Wichtige Angaben')
  await expect(facts).toContainText(`seit ${germanDate(start)}`)
  await expect(facts).toContainText('Miete 800,00 €')
  await expect(facts).toContainText('laufend')
  await expect(page.getByRole('region', { name: 'Mieter' }).getByRole('link', { name: 'erika.musterfrau@example.org' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Kaution' })).toContainText('offen')

  // A rent increase that is already valid.
  const changed = inDays(-10)
  await page.getByRole('button', { name: 'Miete ändern' }).click()
  const rent = page.getByRole('dialog', { name: 'Miete ändern' })
  await rent.getByRole('textbox', { name: 'Gültig ab' }).fill(germanDate(changed))
  await rent.getByLabel('Kaltmiete (€)').fill('680,00')
  await rent.getByRole('button', { name: 'Speichern' }).click()
  await expect(rent).toBeHidden()
  const history = page.getByRole('table', { name: 'Mietverlauf' })
  await expect(history.getByRole('row')).toHaveCount(3)
  await expect(history).toContainText(germanDate(changed))
  await expect(facts).toContainText('Miete 830,00 €')

  // The contract.
  const documents = page.getByRole('region', { name: 'Dokumente' })
  await documents.getByLabel('Dokument auswählen').setInputFiles({
    name: 'Mietvertrag.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n% e2e test contract\n'),
  })
  await expect(documents.getByText('Mietvertrag.pdf')).toBeVisible()
  await expect(documents.getByRole('link', { name: 'Mietvertrag.pdf herunterladen' })).toBeVisible()

  // The tenancy ends; from the next day the unit is vacant.
  const end = inDays(-2)
  await page.getByRole('button', { name: 'Beenden' }).click()
  const ending = page.getByRole('dialog', { name: 'Mietverhältnis beenden' })
  await ending.getByRole('textbox', { name: 'Ende (letzter Tag)' }).fill(germanDate(end))
  await ending.getByRole('button', { name: 'Beenden' }).click()
  await expect(ending).toBeHidden()
  await expect(facts).toContainText(`${germanDate(start)} – ${germanDate(end)}`)
  await expect(facts).toContainText('beendet')
  await expect(page.getByRole('button', { name: 'Mieterdaten löschen' })).toBeVisible()

  await navigate(page, 'Einheiten')
  await expect(card).toContainText(`Leerstand seit ${germanDate(inDays(-1))}`)

  // The list shows running and future tenancies; the ended one with its state.
  await navigate(page, 'Mietverhältnisse')
  await expect(page.getByText('Keine Mietverhältnisse.')).toBeVisible()
  await page.getByRole('combobox', { name: 'Status' }).click()
  await page.getByRole('option', { name: 'Beendet' }).click()
  await expect(page).toHaveURL(/state=ENDED/)
  const row = page.getByRole('row', { name: /Erika Musterfrau/ })
  await expect(row).toContainText(name)
  await expect(row).toContainText('830,00 €')
  await expect(row).toContainText('beendet')
})
