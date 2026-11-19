import { call, clientIp, signIn } from '../support/api'
import { adminPage, expect, test } from '../support/fixtures'
import { readMails } from '../support/mail'
import { freshTotp } from '../support/totp'

/** How the backend reaches the SMTP side of the test mail server. */
const SMTP_HOST = process.env.LOGIS_E2E_SMTP_BACKEND_HOST ?? 'greenmail'
const SMTP_PORT = Number(process.env.LOGIS_E2E_SMTP_BACKEND_PORT ?? 3025)

function decode(raw: string) {
  const bytes = (s: string) =>
    Buffer.from(s.replace(/=\r?\n/g, '').replace(/=([0-9A-F]{2})/g, (_, h) => String.fromCharCode(parseInt(h, 16))), 'binary').toString('utf8')
  return bytes(raw).replace(/=\?UTF-8\?Q\?([^?]*)\?=/gi, (_, q: string) => bytes(q.replace(/_/g, ' ')))
}

test('a sign-in from a new browser sends a warning mail, the same browser again does not', async ({ browser, baseURL, user }) => {
  // The fixture signed the user in once (their first sign-in: no warning). Mail goes through the test server.
  const admin = await adminPage(browser, baseURL!)
  await call(admin.context().request, 'PUT', '/api/admin/smtp', {
    host: SMTP_HOST, port: SMTP_PORT, security: 'NONE', username: '', password: '', fromAddress: 'logis@example.org', fromName: 'Logis',
  })
  await admin.context().close()

  // Another browser: a new device.
  const context = await browser.newContext({ baseURL, extraHTTPHeaders: { 'X-Forwarded-For': clientIp() } })
  const page = await context.newPage()
  await page.goto('/login')
  await page.getByLabel('Benutzername').fill(user.username)
  await page.getByLabel('Passwort').fill(user.password)
  await page.getByRole('button', { name: 'Weiter' }).click()
  await page.getByLabel('Code aus der Authenticator-App').fill(await freshTotp(user.secret!))
  await page.getByRole('button', { name: 'Anmelden' }).click()
  await expect(page).not.toHaveURL(/\/login/)

  let mails: string[] = []
  await expect(async () => {
    mails = (await readMails(user.email)).map(decode)
    expect(mails).toHaveLength(1)
  }).toPass({ timeout: 20_000 })
  expect(mails[0]).toContain('Subject: Neue Anmeldung bei Logis')
  expect(mails[0]).toMatch(/Gerät: \S+ auf \S+/)
  expect(mails[0]).toContain('Alle anderen Geräte abmelden')

  // The same browser again: known device, and the test address has no location.
  await call(context.request, 'POST', '/api/auth/logout')
  await signIn(context.request, user)
  await new Promise((r) => setTimeout(r, 3000))
  expect(await readMails(user.email)).toHaveLength(1)
  await context.close()
})
