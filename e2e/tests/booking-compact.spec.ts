import { clientIp, signIn } from '../support/api'
import { booking, holidayHome } from '../support/data'
import { expect, test } from '../support/fixtures'

test('the compact view and list columns are remembered for the user on another device', async ({ page, api, user, browser, baseURL }) => {
  const home = await holidayHome(api)
  const first = await booking(api, home.unitId, '2030-05-01', '2030-05-04', 'Ida Testgast', 'C-1001', '300.00')
  const second = await booking(api, home.unitId, '2030-06-01', '2030-06-03', 'Theo Testgast', 'C-1002', '200.00')

  // Compact view: only dates, persons and rent; the rent is changed and saved.
  await page.goto(`/ferienvermietung/buchungen/${first.id}`)
  await page.getByRole('radio', { name: 'Kompakt' }).click()
  await expect(page.getByRole('textbox', { name: 'Name' })).toBeHidden()
  await expect(page.getByRole('textbox', { name: 'Buchungsnummer' })).toBeHidden()
  const rent = page.getByRole('textbox', { name: 'Mietpreis (€)' })
  await rent.fill('320,00')
  await page.getByRole('button', { name: 'Speichern' }).click()
  await expect(page.getByLabel('Wichtige Angaben')).toContainText('320,00')

  // A list column hidden in the booking list.
  await page.goto('/ferienvermietung/buchungen')
  await page.getByRole('button', { name: 'Spalten' }).click()
  await page.getByRole('menuitemcheckbox', { name: 'Vermittler' }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('columnheader', { name: /Vermittler/ })).toBeHidden()

  // Another device of the same user: a fresh browser without anything stored.
  const other = await browser.newContext({ baseURL, extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } })
  try {
    await signIn(other.request, user)
    const phone = await other.newPage()
    await phone.goto(`/ferienvermietung/buchungen/${second.id}`)
    await expect(phone.getByRole('radio', { name: 'Kompakt' })).toHaveAttribute('aria-checked', 'true')
    await expect(phone.getByRole('textbox', { name: 'Mietpreis (€)' })).toHaveValue('200,00')
    await expect(phone.getByRole('textbox', { name: 'Name' })).toBeHidden()
    await phone.goto('/ferienvermietung/buchungen')
    await expect(phone.getByRole('row', { name: /Theo Testgast|C-1002/ }).first()).toBeVisible()
    await expect(phone.getByRole('columnheader', { name: /Vermittler/ })).toBeHidden()
  } finally {
    await other.close()
  }
})
