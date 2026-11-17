import { unique } from '../support/api'
import { owner } from '../support/data'
import { expect, test } from '../support/fixtures'

test('a property with two units and a cost that shows in its finances', async ({ page, api }) => {
  const entity = await owner(api)
  const name = unique('Haus ')
  await page.goto('/immobilien')
  await page.getByRole('button', { name: 'Objekt anlegen' }).click()
  const dialog = page.getByRole('dialog', { name: 'Objekt anlegen' })
  await dialog.getByRole('combobox', { name: 'Eigentümer' }).click()
  await page.getByRole('option', { name: entity.name }).click()
  await dialog.getByLabel('Name').fill(name)
  await dialog.getByLabel('Ort').fill('Musterstadt')
  await dialog.getByRole('radio', { name: 'Mehrere Einheiten' }).click()
  await dialog.getByLabel('Erste Einheit').fill('Whg. 1')
  await dialog.getByRole('button', { name: 'Speichern' }).click()
  // A new property opens on its own page.
  await expect(page.getByRole('heading', { name })).toBeVisible()

  await page.getByRole('button', { name: 'Einheit hinzufügen' }).click()
  const unit = page.getByRole('dialog', { name: 'Einheit hinzufügen' })
  await unit.getByLabel('Name').fill('Whg. 2')
  await unit.getByRole('button', { name: 'Speichern' }).click()
  await expect(unit).toBeHidden()
  const units = page.getByRole('region', { name: 'Einheiten' })
  await expect(units.getByText('Whg. 1', { exact: true })).toBeVisible()
  await expect(units.getByText('Whg. 2', { exact: true })).toBeVisible()

  const finances = page.getByRole('region', { name: 'Finanzen' })
  await finances.getByRole('button', { name: 'Kosten' }).click()
  const cost = page.getByRole('dialog', { name: 'Kosten erfassen' })
  await cost.getByLabel('Betrag (€)').fill('123,45')
  await cost.getByLabel('Empfänger').fill('Handwerker Meyer')
  await cost.getByRole('combobox', { name: 'Einheit' }).click()
  await page.getByRole('option', { name: 'Whg. 2' }).click()
  await cost.getByRole('button', { name: 'Speichern' }).click()
  await expect(cost).toBeHidden()
  await expect(finances.getByRole('table', { name: 'Monatsübersicht' })).toContainText('123,45')
})
