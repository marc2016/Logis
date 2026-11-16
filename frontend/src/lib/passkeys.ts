import {
  startAuthentication,
  startRegistration,
  type PublicKeyCredentialCreationOptionsJSON,
  type PublicKeyCredentialRequestOptionsJSON,
} from '@simplewebauthn/browser'
import { api } from './api'

/** The user closed or cancelled the prompt of the device: no message needed. */
export function isPasskeyCancel(error: unknown) {
  const name = error instanceof Error ? error.name : ''
  const code = (error as { code?: string } | null)?.code
  return name === 'NotAllowedError' || name === 'AbortError' || code === 'ERROR_CEREMONY_ABORTED'
}

/** Signs in with a passkey of this site; returns the server's answer for the sign-in page. */
export async function signInWithPasskey<T>() {
  const optionsJSON = await api<PublicKeyCredentialRequestOptionsJSON>('/api/auth/passkey/options', { method: 'POST' })
  const credential = await startAuthentication({ optionsJSON })
  return api<T>('/api/auth/passkey', { json: { credential } })
}

export interface Passkey {
  id: number
  name: string
  createdAt: string
  lastUsedAt: string | null
}

/** Adds a passkey on this device after the current password was confirmed. */
export async function registerPasskey(password: string, name: string) {
  const optionsJSON = await api<PublicKeyCredentialCreationOptionsJSON>('/api/account/passkeys/registration/options', {
    json: { password },
  })
  const credential = await startRegistration({ optionsJSON })
  return api<Passkey>('/api/account/passkeys/registration', { json: { name, credential } })
}
