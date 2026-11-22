import { call } from '../support/api'
import { holidayHome, inDays, germanDate } from '../support/data'
import { adminPage, expect, isPhone, openNavigation, test } from '../support/fixtures'
import { connectMailbox, deliver, fetchMails, fewoConfirmation, readMails } from '../support/mail'
import { APP_TOKEN, pushoverMessages, userKey } from '../support/pushover'

/** How the backend reaches the SMTP side of the test mail server. */
const SMTP_HOST = process.env.LOGIS_E2E_SMTP_BACKEND_HOST ?? 'greenmail'
const SMTP_PORT = Number(process.env.LOGIS_E2E_SMTP_BACKEND_PORT ?? 3025)

function decode(raw: string) {
  const bytes = (s: string) =>
    Buffer.from(s.replace(/=\r?\n/g, '').replace(/=([0-9A-F]{2})/g, (_, h) => String.fromCharCode(parseInt(h, 16))), 'binary').toString('utf8')
  return bytes(raw).replace(/=\?UTF-8\?Q\?([^?]*)\?=/gi, (_, q: string) => bytes(q.replace(/_/g, ' ')))
}

test('set up Pushover, choose notifications and hear about a booking to review', async ({ page, api, user, browser, baseURL }) => {
  // The admin connects Logis to Pushover once (here: the stand-in of the test stack) and sets up outgoing mail.
  const admin = await adminPage(browser, baseURL!)
  await call(admin.context().request, 'PUT', '/api/admin/smtp', {
    host: SMTP_HOST, port: SMTP_PORT, security: 'NONE', username: '', password: '', fromAddress: 'logis@example.org', fromName: 'Logis',
  })
  await admin.goto('/')
  await openNavigation(admin)
  await admin.getByRole('link', { name: 'Pushover' }).click()
  await expect(admin).toHaveURL(/\/einstellungen\/pushover$/)
  // On the phone the navigation sheet stays open over the page: close it.
  if (isPhone(admin)) await admin.keyboard.press('Escape')
  await expect(admin.getByRole('heading', { level: 1, name: 'Pushover' })).toBeVisible()
  await admin.getByLabel('API-Token').fill('falschestoken00000000000000000')
  await admin.getByRole('button', { name: 'Speichern' }).click()
  await expect(admin.getByText('Das Pushover-Token ist ungültig')).toBeVisible()
  await admin.getByLabel('API-Token').fill(APP_TOKEN)
  await admin.getByRole('button', { name: 'Speichern' }).click()
  await expect(admin.getByText(/Ein Token ist hinterlegt/)).toBeVisible()
  await expect(admin.getByLabel('API-Token')).toHaveValue('')
  await admin.context().close()

  // The user stores their own key and tries it out.
  const key = userKey()
  await page.goto('/konto')
  await page.getByLabel('Pushover-Schlüssel').fill('invalidkey0000000000000000000')
  await page.getByRole('button', { name: 'Speichern' }).click()
  await expect(page.getByText('Der Pushover-Schlüssel ist ungültig')).toBeVisible()
  await page.getByLabel('Pushover-Schlüssel').fill(key)
  await page.getByRole('button', { name: 'Speichern' }).click()
  await expect(page.getByText('Ein Schlüssel ist hinterlegt.')).toBeVisible()
  await page.getByRole('button', { name: 'Testnachricht senden' }).click()
  await expect(page.getByText('Testnachricht gesendet.')).toBeVisible()
  expect((await pushoverMessages(key)).map((m) => m.title)).toEqual(['Testnachricht von Logis'])

  // Everything is off, except the sign-in warning by mail, which cannot be switched off.
  const signInMail = page.getByRole('checkbox', { name: 'Neue Anmeldung: E-Mail' })
  await expect(signInMail).toBeChecked()
  await expect(signInMail).toBeDisabled()
  const push = page.getByRole('checkbox', { name: 'Neue Buchung zu prüfen: Pushover' })
  const mail = page.getByRole('checkbox', { name: 'Neue Buchung zu prüfen: E-Mail' })
  await expect(push).not.toBeChecked()
  await push.click()
  await mail.click()
  await expect(push).toBeChecked()
  await page.reload()
  await expect(page.getByRole('checkbox', { name: 'Neue Buchung zu prüfen: Pushover' })).toBeChecked()
  await expect(page.getByRole('checkbox', { name: 'Neue Buchung zu prüfen: E-Mail' })).toBeChecked()
  await expect(page.getByRole('checkbox', { name: 'Anreisen morgen: Pushover' })).not.toBeChecked()

  // A first confirmation teaches Logis which unit the agency means; the user assigns it by hand, so nothing is sent.
  const home = await holidayHome(api)
  const mailbox = `post.${user.username}@example.org`
  await connectMailbox(api, mailbox)
  const stamp = String(Date.now()).slice(-5)
  await deliver(mailbox, fewoConfirmation({ to: mailbox, object: home.name, ref: `1${stamp}`, arrival: inDays(40), departure: inDays(43), guest: 'Max Mustermann' }))
  const [first] = await fetchMails(api, /Neue Buchung/, 1)
  await call(api, 'POST', `/api/vacationrental/mail/${first.id}/assign`, { unitId: home.unitId })

  // The next booking of that agency arrives while nobody is looking.
  const arrival = inDays(50)
  const departure = inDays(57)
  const ref = `2${stamp}`
  await deliver(mailbox, fewoConfirmation({ to: mailbox, object: home.name, ref, arrival, departure, guest: 'Lena Testgast' }))
  const imported = await fetchMails(api, /Neue Buchung/, 2)
  const bookingId = imported.find((m) => m.id !== first.id)!.linkId!

  // Pushover gets the short form: property, period and agency, but nothing about the guest.
  let pushed = await pushoverMessages(key)
  await expect(async () => {
    pushed = await pushoverMessages(key)
    expect(pushed).toHaveLength(2)
  }).toPass({ timeout: 20_000 })
  const note = pushed[1]
  expect(note.title).toBe('Neue Buchung zu prüfen')
  expect(note.message).toBe(`${home.name}, ${germanDate(arrival)} – ${germanDate(departure)} (7 Nächte), über Fewo & Meer`)
  expect(note.url).toBe(`${baseURL}/ferienvermietung/buchungen/${bookingId}`)
  expect(note.url_title).toBe('In Logis öffnen')
  expect(JSON.stringify(pushed)).not.toMatch(/Testgast|Mustermann|312|gast@example\.org/)
  expect(JSON.stringify(pushed)).not.toContain(ref)
  expect(Object.keys(note).sort()).toEqual(['message', 'title', 'token', 'url', 'url_title', 'user'])

  // The mail to the user's own address carries the details.
  let mails: string[] = []
  await expect(async () => {
    mails = (await readMails(user.email)).map(decode).filter((m) => m.includes('Gast: Lena Testgast'))
    expect(mails).toHaveLength(1)
  }).toPass({ timeout: 20_000 })
  expect(mails[0]).toContain(`Objekt: ${home.name}`)
  expect(mails[0]).toContain('Gast: Lena Testgast')
  expect(mails[0]).toContain('Erwartete Miete: 312,00 €')
  expect(mails[0]).toContain(`Buchungsnummer: ${ref}`)
  expect(mails[0]).toContain(`In Logis öffnen: ${baseURL}/ferienvermietung/buchungen/${bookingId}`)

  // The link of the notification opens the booking that waits for review.
  await page.goto(new URL(note.url!).pathname)
  await expect(page.getByRole('heading', { name: 'Lena Testgast' })).toBeVisible()
  await expect(page.getByText('Aus E-Mail übernommen – bitte prüfen')).toBeVisible()

  // Removing the key stops Pushover; the choice stays for a later key.
  await page.goto('/konto')
  await page.getByRole('button', { name: 'Schlüssel entfernen' }).click()
  await page.getByRole('button', { name: 'Ja' }).click()
  await expect(page.getByText('Kein Schlüssel hinterlegt')).toBeVisible()
  const kept = page.getByRole('checkbox', { name: 'Neue Buchung zu prüfen: Pushover' })
  await expect(kept).toBeChecked()
  await expect(kept).toBeDisabled()
  await expect(page.getByText('Pushover: Kein Pushover-Schlüssel hinterlegt')).toBeVisible()
})
