import { unique } from './api'

/** The stand-in for the Pushover API as published for the tests (compose.e2e.yml); the backend reaches it as "pushover". */
const STUB_URL = process.env.LOGIS_E2E_PUSHOVER_URL ?? 'http://127.0.0.1:3080'

/** The only application token the stand-in accepts; invented. */
export const APP_TOKEN = process.env.LOGIS_E2E_PUSHOVER_TOKEN ?? 'e2eapptoken0000000000000000000'

/** What Logis posted to Pushover for one notification. */
export interface PushoverMessage {
  token: string
  user: string
  title: string
  message: string
  url?: string
  url_title?: string
}

/** An invented user key of its own per test, so tests only see their own messages. */
export function userKey() {
  return unique('e2euser').replace(/[^A-Za-z0-9]/g, '')
}

/** The messages the stand-in received for a user key, oldest first. */
export async function pushoverMessages(key: string): Promise<PushoverMessage[]> {
  const res = await fetch(`${STUB_URL}/messages?user=${encodeURIComponent(key)}`)
  if (!res.ok) throw new Error(`Pushover stand-in: ${res.status}`)
  return (await res.json()) as PushoverMessage[]
}
