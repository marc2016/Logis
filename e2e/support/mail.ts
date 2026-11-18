import net from 'node:net'
import type { APIRequestContext } from '@playwright/test'
import { call } from './api'

/** SMTP of the test mail server: in CI published by compose.e2e.yml, locally a GreenMail on the same ports. */
const SMTP_HOST = process.env.LOGIS_E2E_SMTP_HOST ?? '127.0.0.1'
const SMTP_PORT = Number(process.env.LOGIS_E2E_SMTP_PORT ?? 3025)
/** How the backend reaches the IMAP side of the same server. */
export const IMAP_HOST = process.env.LOGIS_E2E_IMAP_HOST ?? 'greenmail'
export const IMAP_PORT = Number(process.env.LOGIS_E2E_IMAP_PORT ?? 3143)

/** Delivers a raw message (CRLF lines) to `to` with a minimal SMTP dialogue. */
export async function deliver(to: string, message: string) {
  const socket = net.createConnection({ host: SMTP_HOST, port: SMTP_PORT })
  socket.setEncoding('utf8')
  let buffer = ''
  const waitFor = (code: string) =>
    new Promise<void>((resolve, reject) => {
      const check = () => {
        const lines = buffer.split('\r\n').filter(Boolean)
        const last = lines.at(-1)
        if (last && /^\d{3} /.test(last)) {
          buffer = ''
          socket.off('data', onData)
          if (last.startsWith(code)) resolve()
          else reject(new Error(`SMTP: expected ${code}, got ${last}`))
        }
      }
      const onData = (chunk: string) => {
        buffer += chunk
        check()
      }
      socket.on('data', onData)
      check()
    })
  const send = (line: string) => socket.write(line + '\r\n')
  await waitFor('220')
  send('HELO e2e')
  await waitFor('250')
  send('MAIL FROM:<buchung@fewoundmeer.de>')
  await waitFor('250')
  send(`RCPT TO:<${to}>`)
  await waitFor('250')
  send('DATA')
  await waitFor('354')
  send(message.replace(/\r?\n/g, '\r\n').replace(/^\./gm, '..') + '\r\n.')
  await waitFor('250')
  send('QUIT')
  socket.end()
}

function germanDate(iso: string) {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

/** A booking confirmation in the format of the agency „Fewo & Meer“, with an invented guest. */
/** `messageId` lets a later mail for the same booking (e.g. with another departure) count as a new mail. */
export function fewoConfirmation(opts: { to: string; object: string; ref: string; arrival: string; departure: string; guest: string; messageId?: string }) {
  const from = germanDate(opts.arrival)
  const until = germanDate(opts.departure)
  return `From: "Fewo & Meer" <buchung@fewoundmeer.de>
To: ${opts.to}
Subject: Neue Buchung für ${opts.object} - Nordhorn
Date: ${new Date().toUTCString().replace('GMT', '+0000')}
Message-ID: <${opts.messageId ?? `fewo-${opts.ref}`}@fewoundmeer.de>
MIME-Version: 1.0
Content-Type: text/html; charset=UTF-8
Content-Transfer-Encoding: 8bit

<html><body>
<p>Sehr geehrte(r) Test GbR,</p>
<p>wir informieren Sie hiermit über den Eingang einer Buchung für Ihr Objekt ${opts.object} - Nordhorn vom ${from} bis ${until}.</p>
<table>
<tr><td><b>Buchungsnummer</b></td><td>${opts.ref}</td></tr>
<tr><td><b>Anzahl Gäste</b></td><td>2 Erwachsene</td></tr>
<tr><td><b>Anreise</b></td><td>${from}</td></tr>
<tr><td><b>Abreise</b></td><td>${until}</td></tr>
<tr><td><b>Mietpreis</b></td><td>312,00 EUR</td></tr>
</table>
<p><b>Informationen zum Gast:</b></p>
<table>
<tr><td><b>Name</b></td><td>${opts.guest}</td></tr>
<tr><td><b>Email</b></td><td>gast@example.org</td></tr>
</table>
<p>Mit freundlichen Grüßen<br>Fewo &amp; Meer</p>
</body></html>
`
}

/** Sets up the user's mailbox on the test mail server; `address` is the mailbox user, the cut-off date defaults to today. */
export async function connectMailbox(api: APIRequestContext, address: string, cutoffDate?: string) {
  const today = cutoffDate ?? new Date().toISOString().slice(0, 10)
  await call(api, 'PUT', '/api/mail/mailbox', {
    host: IMAP_HOST,
    port: IMAP_PORT,
    username: address,
    password: 'beliebig',
    folder: 'INBOX',
    useTls: false,
    cutoffDate: today,
    enabled: true,
  })
}

export interface InboxMail {
  id: number
  subject: string | null
  result: string
  linkId: number | null
}

/** Fetches the mailbox and waits until `count` mails matching `subject` are processed. */
export async function fetchMails(api: APIRequestContext, subject: RegExp, count: number) {
  // Only one fetch runs per mailbox: wait if the regular poll is running.
  for (let i = 0; ; i++) {
    try {
      await call(api, 'POST', '/api/mail/mailbox/poll')
      break
    } catch (e) {
      if (i >= 20 || !String(e).includes(': 409 ')) throw e
      await new Promise((r) => setTimeout(r, 500))
    }
  }
  for (let i = 0; i < 60; i++) {
    const inbox = await call<InboxMail[]>(api, 'GET', '/api/mail/inbox')
    const found = inbox.filter((m) => subject.test(m.subject ?? ''))
    if (found.length >= count) return found
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error(`Mails ${subject} not fetched`)
}
