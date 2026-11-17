import { call, unique } from '../support/api'
import { holidayHome } from '../support/data'
import { expect, test } from '../support/fixtures'

test('rename an agency and merge another one into it', async ({ page, api }) => {
  const kept = await call<{ id: number; name: string }>(api, 'POST', '/api/vacationrental/agencies', { name: unique('Ferienbüro ') })
  const merged = await call<{ id: number; name: string }>(api, 'POST', '/api/vacationrental/agencies', { name: unique('Altbüro ') })
  const home = await holidayHome(api)
  const booking = await call<{ id: number }>(api, 'POST', '/api/vacationrental/bookings', {
    unitId: home.unitId,
    arrival: '2030-09-01',
    departure: '2030-09-03',
    adults: 2,
    agencyId: merged.id,
    guest: { name: 'Nora Testgast' },
  })
  const newName = unique('Ferienbüro Nord ')

  await page.goto('/ferienvermietung/vermittler')
  await page.getByRole('button', { name: `${kept.name} bearbeiten` }).click()
  let dialog = page.getByRole('dialog', { name: 'Vermittler bearbeiten' })
  await dialog.getByLabel('Name', { exact: true }).fill(newName)
  await dialog.getByRole('button', { name: 'Umbenennen' }).click()
  // The old name stays a mail name, so later mails still find the agency.
  await expect(dialog.getByRole('region', { name: 'Mail-Namen' })).toContainText(kept.name)
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()

  await page.getByRole('button', { name: `${merged.name} bearbeiten` }).click()
  dialog = page.getByRole('dialog', { name: 'Vermittler bearbeiten' })
  await dialog.getByRole('combobox', { name: 'Zusammenführen mit' }).click()
  await page.getByRole('option', { name: newName }).click()
  await dialog.getByRole('button', { name: 'Zusammenführen' }).click()
  await dialog.getByRole('button', { name: 'Ja' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('button', { name: `${merged.name} bearbeiten` })).toBeHidden()

  const moved = await call<{ agency: { name: string } }>(api, 'GET', `/api/vacationrental/bookings/${booking.id}`)
  expect(moved.agency.name).toBe(newName)
})
