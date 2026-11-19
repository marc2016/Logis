import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp } from '@/test/renderApp'
import type { AdminUser } from './UsersPage'

const admin: AdminUser = {
  id: 1,
  username: 'admin',
  displayName: 'Anna Admin',
  email: 'admin@example.org',
  role: 'ADMIN',
  enabled: true,
  totpEnabled: true,
  lastLoginAt: '2026-10-25T10:00:00Z',
  createdAt: '2026-10-01T10:00:00Z',
}

function setup(extra: Parameters<typeof mockApi>[0] = {}) {
  mockApi({
    'GET /api/auth/me': authenticated(),
    'GET /api/modules': { body: [] },
    'GET /api/admin/users': { body: [admin] },
    ...extra,
  })
  return renderApp('/einstellungen/benutzer')
}

async function openCreateDialog() {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Benutzer anlegen' }))
  const dialog = await screen.findByRole('dialog')
  await user.type(within(dialog).getByLabelText('Benutzername'), 'anna')
  await user.type(within(dialog).getByLabelText('E-Mail-Adresse'), 'anna@example.org')
  await user.type(within(dialog).getByLabelText('Startpasswort'), 'start-passwort-123')
  await user.click(within(dialog).getByRole('button', { name: 'Anlegen' }))
  return dialog
}

describe('User management', () => {
  it('lists users with status', async () => {
    setup()
    expect(await screen.findByText('Aktiv')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: /Anna Admin/ })).toBeInTheDocument()
    expect(screen.getByText('Eingerichtet')).toBeInTheDocument()
  })

  it('creates a user', async () => {
    let body: unknown
    setup({
      'POST /api/admin/users': (init) => {
        body = JSON.parse(String(init?.body))
        return { status: 201, body: { ...admin, id: 2, username: 'anna', role: 'USER', totpEnabled: false } }
      },
    })
    await openCreateDialog()
    expect(await screen.findByText('Benutzer „anna“ angelegt.')).toBeInTheDocument()
    expect(body).toEqual({ username: 'anna', displayName: '', email: 'anna@example.org', role: 'USER', password: 'start-passwort-123' })
  })

  it('shows the address and changes it', async () => {
    let body: unknown
    setup({
      'GET /api/admin/users': { body: [admin, { ...admin, id: 2, username: 'bert', displayName: 'Bert', email: null, role: 'USER' }] },
      'PATCH /api/admin/users/2': (init) => {
        body = JSON.parse(String(init?.body))
        return { body: {} }
      },
    })
    expect(await screen.findByText('admin@example.org')).toBeInTheDocument()
    expect(screen.getByText('Keine E-Mail-Adresse')).toBeInTheDocument()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Aktionen für bert' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Bearbeiten' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText('E-Mail-Adresse')).toHaveValue('')
    await user.type(within(dialog).getByLabelText('E-Mail-Adresse'), 'bert@example.org')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(body).toEqual({ displayName: 'Bert', role: 'USER', email: 'bert@example.org' }))
  })

  it('shows the message for a duplicate username', async () => {
    setup({ 'POST /api/admin/users': { status: 409, body: { detail: 'Benutzername ist bereits vergeben' } } })
    const dialog = await openCreateDialog()
    expect(await within(dialog).findByText('Benutzername ist bereits vergeben')).toBeInTheDocument()
  })

  it('shows the message when the last admin would be downgraded', async () => {
    setup({
      'PATCH /api/admin/users/1': {
        status: 409,
        body: { detail: 'Es muss mindestens ein aktiver Admin bestehen bleiben' },
      },
    })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Aktionen für admin' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Bearbeiten' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('combobox'))
    await user.click(await screen.findByRole('option', { name: 'Benutzer' }))
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    expect(await within(dialog).findByText('Es muss mindestens ein aktiver Admin bestehen bleiben')).toBeInTheDocument()
  })

  it('cannot disable the own account', async () => {
    setup()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Aktionen für admin' }))
    expect(await screen.findByRole('menuitem', { name: 'Deaktivieren' })).toHaveAttribute('data-disabled')
  })

  it('asks for a fresh sign-in when a remembered device started the session', async () => {
    const me = authenticated()
    let reauthenticated = false
    const { router } = setup({
      'GET /api/auth/me': () =>
        reauthenticated
          ? { body: { stage: 'ANONYMOUS', user: null, recoveryCodes: null } }
          : { ...me, body: { ...me.body, reauthenticationRequired: true } },
      'POST /api/auth/reauthenticate': () => {
        reauthenticated = true
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    expect(await screen.findByText('Für den Admin-Bereich bitte erneut anmelden')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Erneut anmelden' }))
    expect(await screen.findByRole('checkbox', { name: 'Angemeldet bleiben' })).toBeChecked()
    expect(reauthenticated).toBe(true)
    expect(router.state.location.search).toBe('?next=%2Feinstellungen%2Fbenutzer')
  })

  it('shows the notice when an admin request is refused for a remembered device', async () => {
    const me = authenticated()
    let restored = false
    setup({
      'GET /api/auth/me': () => ({ ...me, body: { ...me.body, reauthenticationRequired: restored } }),
      'GET /api/admin/users': () => {
        restored = true
        return {
          status: 403,
          body: { type: 'urn:logis:reauthentication-required', detail: 'Für den Admin-Bereich bitte erneut anmelden' },
        }
      },
    })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Erneut anmelden' })).toBeInTheDocument())
  })
})
