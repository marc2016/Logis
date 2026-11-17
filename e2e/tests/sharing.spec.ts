import { call } from '../support/api'
import { holidayHome, share } from '../support/data'
import { expect, secondUser, test } from '../support/fixtures'

test('a viewer sees the guest name but no contact data and cannot change the booking', async ({ api, browser, baseURL }) => {
  const home = await holidayHome(api)
  const created = await call<{ id: number }>(api, 'POST', '/api/vacationrental/bookings', {
    unitId: home.unitId,
    arrival: '2030-05-02',
    departure: '2030-05-05',
    adults: 2,
    guest: { name: 'Ida Testgast', phone: '0170 0000099', email: 'ida@example.org' },
  })
  const viewer = await secondUser(browser, baseURL!, 'viewer')
  await share(api, 'property', home.propertyId, viewer.user.id, 'VIEWER')

  await viewer.page.goto(`/ferienvermietung/buchungen/${created.id}`)
  await expect(viewer.page.getByRole('heading', { name: 'Ida Testgast' })).toBeVisible()
  await expect(viewer.page.getByLabel('E-Mail')).toBeHidden()
  await expect(viewer.page.getByText('ida@example.org')).toBeHidden()
  await expect(viewer.page.getByRole('button', { name: 'Speichern' })).toBeHidden()
  await expect(viewer.page.getByRole('region', { name: 'Quelle' })).toBeHidden()
  await viewer.page.context().close()
})

test('an editor of one unit sees only that unit and records a booking there', async ({ api, browser, baseURL }) => {
  const entity = await call<{ id: number }>(api, 'POST', '/api/legal-entities', { name: `Test GbR ${Date.now()}`, kind: 'GBR' })
  const property = await call<{ id: number; name: string; units: { id: number; name: string }[] }>(api, 'POST', '/api/properties', {
    legalEntityId: entity.id,
    name: `Strandhaus ${Date.now()}`,
    city: 'Seehausen',
    multiUnit: true,
    firstUnit: { name: 'Whg. 1', usage: 'VACATION_RENTAL' },
  })
  await call(api, 'POST', `/api/properties/${property.id}/units`, { name: 'Whg. 2', usage: 'VACATION_RENTAL' })
  const editor = await secondUser(browser, baseURL!, 'editor')
  await share(api, 'unit', property.units[0].id, editor.user.id, 'EDITOR')

  const units = await call<{ name: string }[]>(editor.api, 'GET', '/api/vacationrental/units')
  expect(units.map((u) => u.name)).toEqual(['Whg. 1'])

  const page = editor.page
  await page.goto('/ferienvermietung/buchungen')
  await page.getByRole('button', { name: 'Buchung' }).click()
  const dialog = page.getByRole('dialog', { name: 'Buchung erfassen' })
  await expect(dialog.getByRole('combobox', { name: 'Einheit' })).toContainText('Whg. 1')
  await dialog.getByLabel('Anreise').fill('2030-07-01')
  await dialog.getByLabel('Abreise').fill('2030-07-04')
  await dialog.getByRole('textbox', { name: 'Name' }).fill('Theo Testgast')
  await dialog.getByRole('button', { name: 'Speichern' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('cell', { name: 'Theo Testgast', exact: true })).toBeVisible()
  await page.context().close()
})
