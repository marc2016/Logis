import { signIn } from '../support/api'
import { account, booking, holidayHome, inDays, owner } from '../support/data'
import { expect, test } from '../support/fixtures'

const DEFAULT = ['Zu erledigen', 'Nächste An- und Abreisen', 'Belegung', 'Kontostände']

test('the start page shows the default tiles and keeps a customised arrangement', async ({ page, api, user }) => {
  const home = await holidayHome(api)
  await booking(api, home.unitId, inDays(3), inDays(6), 'Ida Testgast')
  const entity = await owner(api)
  await account(api, entity.id, 'Startkonto', '250.00')
  const titles = () => page.getByRole('heading', { level: 2 }).allTextContents()

  await page.goto('/')
  await expect.poll(titles).toEqual(DEFAULT)
  const arrivals = page.getByRole('region', { name: 'Nächste An- und Abreisen' })
  await expect(arrivals).toContainText('Anreise')
  await expect(arrivals).toContainText('Ida Testgast')
  await expect(page.getByRole('region', { name: 'Kontostände' })).toContainText('Startkonto')

  // A second occupancy tile for the holiday home, „Kontostände“ moved up, the arrivals removed.
  await page.getByRole('button', { name: 'Anpassen' }).click()
  await page.getByRole('button', { name: 'Kachel hinzufügen' }).click()
  await page.getByRole('button', { name: 'Belegung hinzufügen' }).click()
  await expect(page.getByRole('region', { name: 'Belegung' })).toHaveCount(2)
  await page.getByRole('button', { name: 'Belegung einstellen' }).last().click()
  await page.getByRole('combobox', { name: 'Einheit' }).click()
  await page.getByRole('option', { name: home.name }).click()
  await page.getByRole('button', { name: 'Übernehmen' }).click()
  await page.getByRole('button', { name: 'Kontostände nach oben' }).click()
  await page.getByRole('button', { name: 'Nächste An- und Abreisen entfernen' }).click()
  await page.getByRole('button', { name: 'Fertig' }).click()
  const custom = ['Zu erledigen', 'Kontostände', 'Belegung', 'Belegung']
  await expect.poll(titles).toEqual(custom)
  await expect(page.getByRole('region', { name: 'Belegung' }).last().getByRole('link', { name: 'Zur Auswertung' }))
    .toHaveAttribute('href', new RegExp(`unitId=${home.unitId}`))

  // Kept after a reload and after signing in again.
  await page.reload()
  await expect.poll(titles).toEqual(custom)
  await page.context().clearCookies()
  await signIn(page.context().request, user)
  await page.goto('/')
  await expect.poll(titles).toEqual(custom)

  // Back to the default.
  await page.getByRole('button', { name: 'Anpassen' }).click()
  await page.getByRole('button', { name: 'Standard wiederherstellen' }).click()
  await page.getByRole('button', { name: 'Wiederherstellen' }).click()
  await page.getByRole('button', { name: 'Fertig' }).click()
  await expect.poll(titles).toEqual(DEFAULT)
})
