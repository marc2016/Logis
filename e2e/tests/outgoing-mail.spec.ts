import { call } from '../support/api'
import { adminPage, expect, test } from '../support/fixtures'
import { readMails } from '../support/mail'

/** How the backend reaches the SMTP side of the test mail server. */
const SMTP_HOST = process.env.LOGIS_E2E_SMTP_BACKEND_HOST ?? 'greenmail'
const SMTP_PORT = Number(process.env.LOGIS_E2E_SMTP_BACKEND_PORT ?? 3025)

/** Mail bodies and subjects as the test needs them: quoted-printable and encoded words decoded. */
function decode(raw: string) {
  const bytes = (s: string) =>
    Buffer.from(s.replace(/=\r?\n/g, '').replace(/=([0-9A-F]{2})/g, (_, h) => String.fromCharCode(parseInt(h, 16))), 'binary').toString('utf8')
  return bytes(raw).replace(/=\?UTF-8\?Q\?([^?]*)\?=/gi, (_, q: string) => bytes(q.replace(/_/g, ' ')))
}

async function waitForMails(address: string, count: number) {
  let mails: string[] = []
  await expect(async () => {
    mails = (await readMails(address)).map(decode)
    expect(mails).toHaveLength(count)
  }).toPass({ timeout: 20_000 })
  return mails
}

test('admin sets up outgoing mail, a user changes their address with a code', async ({ page, browser, baseURL, user }) => {
  // The admin configures the test mail server and sends a test mail.
  const admin = await adminPage(browser, baseURL!)
  await admin.goto('/einstellungen/e-mail-versand')
  await admin.getByLabel('Mailserver').fill(SMTP_HOST)
  await admin.getByRole('combobox', { name: 'Verschlüsselung' }).click()
  await admin.getByRole('option', { name: 'Keine' }).click()
  await admin.getByLabel('Port').fill(String(SMTP_PORT))
  await admin.getByLabel('Benutzername').fill('')
  await admin.getByLabel('Absenderadresse').fill('logis@example.org')
  await admin.getByRole('button', { name: 'Speichern' }).click()
  await expect(admin.getByText('Mailserver gespeichert und erreichbar.')).toBeVisible()

  const testTo = `test-${user.username}@example.org`
  await admin.getByLabel('Testmail an').fill(testTo)
  await admin.getByRole('button', { name: 'Testmail senden' }).click()
  await expect(admin.getByText(`Testmail an ${testTo} gesendet.`)).toBeVisible()
  const [testMail] = await waitForMails(testTo, 1)
  expect(testMail).toContain('Subject: Testmail von Logis')
  await admin.context().close()

  // The user changes their address under „Mein Konto“ with the code sent to the new address.
  const newAddress = `neu-${user.username}@example.org`
  await page.goto('/konto')
  await expect(page.getByText(user.email, { exact: true })).toBeVisible()
  await page.getByLabel('Neue E-Mail-Adresse').fill(newAddress)
  await page.getByRole('button', { name: 'Code senden' }).click()
  const [codeMail] = await waitForMails(newAddress, 1)
  const code = /\b(\d{6})\b/.exec(codeMail.split(/\r?\n\r?\n/).slice(1).join('\n'))![1]
  await page.getByLabel('Bestätigungscode').fill(code)
  await page.getByRole('button', { name: 'Bestätigen' }).click()
  await expect(page.getByText('E-Mail-Adresse geändert.')).toBeVisible()
  await expect(page.getByText(newAddress, { exact: true })).toBeVisible()

  // The old address learns about the change, without the full new address.
  const [notice] = await waitForMails(user.email, 1)
  expect(notice).toContain('Deine E-Mail-Adresse in Logis wurde geändert.')
  expect(notice).toContain('n…@example.org')
  expect(notice).not.toContain(newAddress)
  expect((await call<{ email: string }>(page.context().request, 'GET', '/api/account/email')).email).toBe(newAddress)
})
