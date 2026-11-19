import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp } from '@/test/renderApp'

const configured = {
  configured: true,
  host: 'smtp.example.org',
  port: 587,
  security: 'STARTTLS',
  username: 'logis',
  hasPassword: true,
  fromAddress: 'logis@example.org',
  fromName: 'Logis',
  updatedAt: '2026-11-19T09:00:00Z',
}

function setup(extra: Parameters<typeof mockApi>[0] = {}) {
  mockApi({
    'GET /api/auth/me': authenticated(),
    'GET /api/modules': { body: [] },
    'GET /api/admin/smtp': { body: configured },
    'GET /api/account/email': { body: { email: 'admin@example.org', pendingEmail: null, canChange: true } },
    ...extra,
  })
  renderApp('/einstellungen/e-mail-versand')
}

describe('Outgoing mail server', () => {
  it('loads the settings without a password and saves the fields', async () => {
    let sent: unknown
    setup({
      'PUT /api/admin/smtp': (init) => {
        sent = JSON.parse(String(init?.body))
        return { body: configured }
      },
    })
    const user = userEvent.setup()
    expect(await screen.findByLabelText('Mailserver')).toHaveValue('smtp.example.org')
    const password = screen.getByLabelText('Passwort')
    expect(password).toHaveValue('')
    expect(password).toHaveAttribute('placeholder', expect.stringContaining('gespeichert'))
    await user.clear(screen.getByLabelText('Absendername'))
    await user.type(screen.getByLabelText('Absendername'), 'Logis Ferienhaus')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() =>
      expect(sent).toEqual({
        host: 'smtp.example.org',
        port: 587,
        security: 'STARTTLS',
        username: 'logis',
        password: '',
        fromAddress: 'logis@example.org',
        fromName: 'Logis Ferienhaus',
      }),
    )
  })

  it('shows the server message', async () => {
    setup({
      'PUT /api/admin/smtp': {
        status: 400,
        body: { detail: 'Der Mailserver ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen' },
      },
    })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Der Mailserver ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen')).toBeInTheDocument()
  })

  it('sends a test mail to the entered address', async () => {
    let sent: unknown
    setup({
      'POST /api/admin/smtp/test': (init) => {
        sent = JSON.parse(String(init?.body))
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    const to = await screen.findByLabelText('Testmail an')
    await waitFor(() => expect(to).toHaveValue('admin@example.org'))
    await user.clear(to)
    await user.type(to, 'test@example.org')
    await user.click(screen.getByRole('button', { name: 'Testmail senden' }))
    await waitFor(() => expect(sent).toEqual({ to: 'test@example.org' }))
    expect(await screen.findByText('Testmail an test@example.org gesendet.')).toBeInTheDocument()
  })

  it('asks before removing the configuration', async () => {
    let removed = false
    setup({
      'DELETE /api/admin/smtp': () => {
        removed = true
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Konfiguration entfernen' }))
    expect(removed).toBe(false)
    await user.click(screen.getByRole('button', { name: 'Ja' }))
    await waitFor(() => expect(removed).toBe(true))
  })

  it('without configuration there is no test mail', async () => {
    setup({
      'GET /api/admin/smtp': {
        body: { configured: false, host: null, port: null, security: null, username: null, hasPassword: false, fromAddress: null, fromName: null, updatedAt: null },
      },
    })
    expect(await screen.findByText(/Noch nicht eingerichtet/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Testmail senden' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Konfiguration entfernen' })).not.toBeInTheDocument()
  })
})
