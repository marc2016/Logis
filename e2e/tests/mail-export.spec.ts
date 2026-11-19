import fs from 'node:fs'
import { call } from '../support/api'
import { adminPage, expect, test } from '../support/fixtures'
import { deliver, fewoConfirmation, IMAP_HOST, IMAP_PORT } from '../support/mail'
import { unzip } from '../support/zip'

test('an admin exports pseudonymized mails of a consenting mailbox', async ({ page, api, user, browser, baseURL }) => {
  const address = `${user.username}@example.org`
  const ref = String(Date.now()).slice(-6)
  const guest = 'Lotte Exportgast'
  await deliver(address, fewoConfirmation({ to: address, object: `Haus ${ref}`, ref, arrival: '2027-03-01', departure: '2027-03-05', guest }))
  await call(api, 'PUT', '/api/mail/mailbox', {
    host: IMAP_HOST, port: IMAP_PORT, username: address, password: 'beliebig', folder: 'INBOX', useTls: false,
    cutoffDate: '2020-01-01', enabled: true,
  })
  await call(api, 'POST', '/api/mail/mailbox/poll')
  await expect(async () => {
    expect(await call<unknown[]>(api, 'GET', '/api/mail/inbox')).toHaveLength(1)
  }).toPass({ timeout: 30_000 })

  // The owner allows the export in the mailbox settings.
  await page.goto('/posteingang')
  // The box follows the saved state, so it turns checked once the server confirmed.
  const consent = page.getByRole('checkbox', { name: 'Admins dürfen meine Mails pseudonymisiert exportieren' })
  await consent.click()
  await expect(consent).toBeChecked()

  // The admin exports only this mailbox.
  const admin = await adminPage(browser, baseURL!)
  await admin.goto('/einstellungen/mail-export')
  const mailboxes = admin.getByRole('group', { name: 'Postfächer' }).getByRole('checkbox')
  await expect(admin.getByRole('checkbox', { name: `Postfach von ${user.displayName}` })).toBeVisible()
  for (const box of await mailboxes.all()) {
    const mine = (await box.getAttribute('aria-label')) === `Postfach von ${user.displayName}`
    await box.setChecked(mine)
  }
  await admin.getByRole('button', { name: 'Exportieren' }).click()
  await expect(admin.getByText('Pseudonymisiert nach bestem Bemühen.', { exact: false })).toBeVisible()
  const downloading = admin.waitForEvent('download')
  await admin.getByRole('button', { name: 'Herunterladen' }).click()
  const download = await downloading
  expect(download.suggestedFilename()).toMatch(/^logis-mails-\d{4}-\d{2}-\d{2}\.zip$/)

  const files = unzip(fs.readFileSync(await download.path()))
  expect([...files.keys()].filter((n) => n.endsWith('.eml'))).toHaveLength(1)
  const all = [...files.values()].map((b) => b.toString('utf8')).join('\n')
  expect(all).not.toContain('Lotte')
  expect(all).not.toContain('Exportgast')
  expect(all).not.toContain(ref)
  expect(all).toContain('Gast 1')
  expect(files.get('index.csv')!.toString('utf8')).toContain(user.username)

  // The export shows in the log and to the owner.
  await expect(admin.getByRole('table', { name: 'Protokoll' }).getByRole('row', { name: new RegExp(user.displayName) }).first()).toBeVisible()
  await admin.context().close()
  await page.reload()
  await expect(page.getByText(/Zuletzt exportiert am .* · 1 Mail · von /)).toBeVisible()
})
