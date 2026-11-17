import { call, unique } from '../support/api'
import { account, holidayHome, owner } from '../support/data'
import { expect, test } from '../support/fixtures'

test('owner, accounts, transactions and a transfer with correct balances', async ({ page, api }) => {
  const ownerName = unique('Muster GbR ')
  await page.goto('/finanzen/eigentuemer')
  await page.getByRole('button', { name: 'Eigentümer anlegen' }).click()
  const ownerDialog = page.getByRole('dialog', { name: 'Eigentümer anlegen' })
  await ownerDialog.getByLabel('Name').fill(ownerName)
  await ownerDialog.getByRole('button', { name: 'Speichern' }).click()
  await expect(ownerDialog).toBeHidden()
  await expect(page.getByText(ownerName)).toBeVisible()

  const giro = unique('Girokonto ')
  await page.goto('/finanzen/konten')
  await page.getByRole('button', { name: 'Konto anlegen' }).click()
  const accountDialog = page.getByRole('dialog', { name: 'Konto anlegen' })
  await accountDialog.getByRole('combobox', { name: 'Eigentümer' }).click()
  await page.getByRole('option', { name: ownerName }).click()
  await accountDialog.getByLabel('Name').fill(giro)
  await accountDialog.getByLabel('Anfangssaldo (€)').fill('1000,00')
  await accountDialog.getByRole('button', { name: 'Speichern' }).click()
  await expect(accountDialog).toBeHidden()

  const owners = await call<{ id: number; name: string }[]>(api, 'GET', '/api/legal-entities')
  const savings = await account(api, owners.find((o) => o.name === ownerName)!.id, unique('Tagesgeld '))

  await page.getByRole('link', { name: new RegExp(giro) }).click()
  await page.getByRole('button', { name: 'Transaktion erfassen' }).click()
  const tx = page.getByRole('dialog', { name: 'Transaktion erfassen' })
  await tx.getByLabel('Betrag (€)').fill('84,90')
  await tx.getByLabel('Gegenpartei').fill('Supermarkt am Markt')
  await tx.getByRole('button', { name: 'Speichern' }).click()
  await expect(tx).toBeHidden()
  await expect(page.getByText('915,10 €').first()).toBeVisible()

  await page.getByRole('button', { name: 'Umbuchung' }).click()
  const transfer = page.getByRole('dialog', { name: 'Umbuchung' })
  await transfer.getByRole('combobox', { name: 'Nach' }).click()
  await page.getByRole('option', { name: savings.name }).click()
  await transfer.getByLabel('Betrag (€)').fill('300,00')
  await transfer.getByRole('button', { name: 'Speichern' }).click()
  await expect(transfer).toBeHidden()
  await expect(page.getByText('615,10 €').first()).toBeVisible()

  await page.goto(`/finanzen/konten/${savings.id}`)
  await expect(page.getByText('300,00 €').first()).toBeVisible()
})

test('filter transactions and assign a category and a property to several at once', async ({ page, api }) => {
  const entity = await owner(api)
  const giro = await account(api, entity.id)
  const home = await holidayHome(api)
  const shop = unique('Laden ')
  for (const amount of ['-12.50', '-20.00', '-7.30']) {
    await call(api, 'POST', '/api/transactions', { accountId: giro.id, bookingDate: '2030-02-03', amount, counterparty: shop })
  }
  await call(api, 'POST', '/api/transactions', { accountId: giro.id, bookingDate: '2030-02-04', amount: '-99.00', counterparty: 'Andere Firma' })

  await page.goto('/finanzen/transaktionen')
  await page.getByLabel('Suche').fill(shop)
  const rows = page.getByRole('row').filter({ hasText: shop })
  await expect(rows).toHaveCount(3)
  await expect(page.getByText('Andere Firma')).toBeHidden()

  await page.getByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }).click()
  const bar = page.getByRole('toolbar', { name: 'Aktionen für die Auswahl' })
  await expect(bar.getByText('3 ausgewählt')).toBeVisible()
  await bar.getByRole('button', { name: 'Kategorie zuordnen' }).click()
  const categoryDialog = page.getByRole('dialog', { name: 'Kategorie zuordnen' })
  await categoryDialog.getByRole('combobox', { name: 'Kategorie' }).click()
  await page.getByRole('option', { name: 'Lebensmittel', exact: true }).click()
  await categoryDialog.getByRole('button', { name: 'Zuordnen' }).click()
  await expect(page.getByText('3 zugeordnet')).toBeVisible()

  await page.getByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }).click()
  await bar.getByRole('button', { name: 'Objekt zuordnen' }).click()
  const targetDialog = page.getByRole('dialog', { name: 'Objekt zuordnen' })
  await targetDialog.getByRole('combobox', { name: 'Objekt oder Einheit' }).click()
  await page.getByRole('option', { name: home.name }).click()
  await targetDialog.getByRole('button', { name: 'Zuordnen' }).click()
  await expect(page.getByText('3 zugeordnet').last()).toBeVisible()

  const list = await call<{ items: { counterparty: string; categoryName: string | null; targetName: string | null }[] }>(
    api, 'GET', `/api/transactions?q=${encodeURIComponent(shop)}&size=50`,
  )
  expect(list.items).toHaveLength(3)
  expect(list.items.every((t) => t.categoryName === 'Lebensmittel' && t.targetName?.includes(home.name))).toBe(true)
})
