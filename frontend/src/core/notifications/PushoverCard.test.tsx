import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, normalUser, renderApp } from '@/test/renderApp'
import type { PushoverAccount } from './api'

const KEY = 'userkey00000000000000000000000'
const withKey: PushoverAccount = { available: true, hasKey: true, invalid: false, updatedAt: '2026-11-21T09:00:00Z' }
const withoutKey: PushoverAccount = { available: true, hasKey: false, invalid: false, updatedAt: null }

function setup(account: PushoverAccount, extra: Parameters<typeof mockApi>[0] = {}) {
  mockApi({
    'GET /api/auth/me': authenticated(normalUser),
    'GET /api/modules': { body: [] },
    'GET /api/notifications': { body: { channels: [], types: [] } },
    'GET /api/account/pushover': { body: account },
    ...extra,
  })
  renderApp('/konto')
}

describe('Own Pushover key', () => {
  it('says that Pushover is not set up and offers no input', async () => {
    setup({ available: false, hasKey: false, invalid: false, updatedAt: null })
    expect(await screen.findByText(/Pushover ist noch nicht eingerichtet/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Pushover-Schlüssel')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Testnachricht senden' })).not.toBeInTheDocument()
  })

  it('stores the key and never shows it again', async () => {
    let sent: unknown
    setup(withoutKey, {
      'PUT /api/account/pushover': (init) => {
        sent = JSON.parse(String(init?.body))
        return { body: withKey }
      },
    })
    const user = userEvent.setup()
    expect(await screen.findByText('Kein Schlüssel hinterlegt')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Pushover-Schlüssel'), ` ${KEY}`)
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(sent).toEqual({ userKey: KEY }))
    expect(await screen.findByText('Ein Schlüssel ist hinterlegt.')).toBeInTheDocument()
    expect(screen.getByLabelText('Neuer Pushover-Schlüssel')).toHaveValue('')
    expect(screen.queryByText(KEY)).not.toBeInTheDocument()
  })

  it('shows the message for a key Pushover does not know', async () => {
    setup(withoutKey, { 'PUT /api/account/pushover': { status: 400, body: { detail: 'Der Pushover-Schlüssel ist ungültig' } } })
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Pushover-Schlüssel'), 'unknownkey00000000000000000000')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Der Pushover-Schlüssel ist ungültig')).toBeInTheDocument()
  })

  it('sends a test notification and reports a failure', async () => {
    let fail = false
    let tests = 0
    setup(withKey, {
      'POST /api/account/pushover/test': () => {
        tests += 1
        return fail ? { status: 400, body: { detail: 'Die Testnachricht konnte nicht gesendet werden' } } : { status: 204 }
      },
    })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Testnachricht senden' }))
    expect(await screen.findByText('Testnachricht gesendet.')).toBeInTheDocument()
    fail = true
    await user.click(screen.getByRole('button', { name: 'Testnachricht senden' }))
    expect(await screen.findByText('Die Testnachricht konnte nicht gesendet werden')).toBeInTheDocument()
    expect(tests).toBe(2)
  })

  it('says when the key is no longer valid and offers no test', async () => {
    setup({ ...withKey, invalid: true })
    expect(await screen.findByText(/Der Pushover-Schlüssel ist nicht mehr gültig/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Testnachricht senden' })).toBeDisabled()
    expect(screen.getByLabelText('Neuer Pushover-Schlüssel')).toBeInTheDocument()
  })

  it('asks before removing the key', async () => {
    let removed = false
    setup(withKey, {
      'DELETE /api/account/pushover': () => {
        removed = true
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Schlüssel entfernen' }))
    expect(removed).toBe(false)
    await user.click(screen.getByRole('button', { name: 'Ja' }))
    await waitFor(() => expect(removed).toBe(true))
    expect(await screen.findByText('Pushover-Schlüssel entfernt.')).toBeInTheDocument()
  })
})
