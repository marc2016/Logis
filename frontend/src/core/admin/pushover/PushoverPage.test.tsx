import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, normalUser, renderApp } from '@/test/renderApp'

const TOKEN = 'apptoken0000000000000000000000'
const configured = { configured: true, updatedAt: '2026-11-21T09:00:00Z' }
const empty = { configured: false, updatedAt: null }

function setup(settings: typeof configured | typeof empty, extra: Parameters<typeof mockApi>[0] = {}) {
  mockApi({
    'GET /api/auth/me': authenticated(),
    'GET /api/modules': { body: [] },
    'GET /api/admin/pushover': { body: settings },
    ...extra,
  })
  renderApp('/einstellungen/pushover')
}

describe('Pushover application', () => {
  it('is reached from the settings in the sidebar and says what is sent to Pushover', async () => {
    setup(empty)
    expect(await screen.findByRole('link', { name: 'Pushover' })).toHaveAttribute('href', '/einstellungen/pushover')
    expect(await screen.findByText(/Noch nicht eingerichtet/)).toBeInTheDocument()
    expect(screen.getByText(/Logis sendet nur den Titel, einen kurzen Text und den Link/)).toBeInTheDocument()
    expect(screen.getByText(/Namen und Kontaktdaten von Gästen, Beträge und Buchungsnummern werden nicht an Pushover gesendet/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Token entfernen' })).not.toBeInTheDocument()
  })

  it('stores the token and shows only that one is set', async () => {
    let sent: unknown
    let stored = false
    setup(empty, {
      'GET /api/admin/pushover': () => ({ body: stored ? configured : empty }),
      'PUT /api/admin/pushover': (init) => {
        sent = JSON.parse(String(init?.body))
        stored = true
        return { body: configured }
      },
    })
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('API-Token'), TOKEN)
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(sent).toEqual({ token: TOKEN }))
    expect(await screen.findByText(/Ein Token ist hinterlegt/)).toBeInTheDocument()
    expect(screen.getByLabelText('API-Token')).toHaveValue('')
    expect(screen.queryByDisplayValue(TOKEN)).not.toBeInTheDocument()
  })

  it('shows the message for a token Pushover rejects', async () => {
    setup(configured, { 'PUT /api/admin/pushover': { status: 400, body: { detail: 'Das Pushover-Token ist ungültig' } } })
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('API-Token'), 'wrongtoken00000000000000000000')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Das Pushover-Token ist ungültig')).toBeInTheDocument()
  })

  it('asks before removing the token', async () => {
    let removed = false
    setup(configured, {
      'DELETE /api/admin/pushover': () => {
        removed = true
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Token entfernen' }))
    expect(removed).toBe(false)
    await user.click(screen.getByRole('button', { name: 'Ja' }))
    await waitFor(() => expect(removed).toBe(true))
  })

  it('is not offered to ordinary users', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(normalUser), 'GET /api/modules': { body: [] } })
    renderApp('/einstellungen/pushover')
    await waitFor(() => expect(screen.queryByLabelText('API-Token')).not.toBeInTheDocument())
    expect(screen.queryByRole('link', { name: 'Pushover' })).not.toBeInTheDocument()
  })
})
