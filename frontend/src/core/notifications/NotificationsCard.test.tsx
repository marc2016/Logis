import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, normalUser, renderApp } from '@/test/renderApp'
import type { NotificationSettings } from './api'

function settings(overrides: Partial<NotificationSettings> = {}): NotificationSettings {
  return {
    channels: [
      { id: 'mail', label: 'E-Mail', usable: true, hint: null },
      { id: 'pushover', label: 'Pushover', usable: true, hint: null },
    ],
    types: [
      {
        key: 'core.signIn',
        label: 'Neue Anmeldung',
        group: 'Sicherheit',
        channels: { mail: { enabled: true, mandatory: true }, pushover: { enabled: false, mandatory: false } },
      },
      {
        key: 'vacationrental.bookingReview',
        label: 'Neue Buchung zu prüfen',
        group: 'Ferienvermietung',
        channels: { mail: { enabled: false, mandatory: false }, pushover: { enabled: true, mandatory: false } },
      },
      {
        key: 'vacationrental.arrivals',
        label: 'Anreisen morgen',
        group: 'Ferienvermietung',
        channels: { mail: { enabled: false, mandatory: false }, pushover: { enabled: false, mandatory: false } },
      },
    ],
    ...overrides,
  }
}

function setup(body: NotificationSettings, extra: Parameters<typeof mockApi>[0] = {}) {
  mockApi({
    'GET /api/auth/me': authenticated(normalUser),
    'GET /api/modules': { body: [] },
    'GET /api/notifications': { body },
    'GET /api/account/pushover': { body: { available: true, hasKey: true, invalid: false, updatedAt: '2026-11-21T09:00:00Z' } },
    ...extra,
  })
  renderApp('/konto')
}

describe('Notifications', () => {
  it('lists the event types by group with one switch per channel', async () => {
    setup(settings())
    const table = await screen.findByRole('table')
    expect(within(table).getByRole('columnheader', { name: 'E-Mail' })).toBeInTheDocument()
    expect(within(table).getByRole('columnheader', { name: 'Pushover' })).toBeInTheDocument()
    const rows = within(table).getAllByRole('row').map((row) => row.textContent)
    expect(rows).toEqual(['EreignisE-MailPushover', 'Sicherheit', 'Neue Anmeldung', 'Ferienvermietung', 'Neue Buchung zu prüfen', 'Anreisen morgen'])
    expect(screen.getByRole('checkbox', { name: 'Neue Buchung zu prüfen: Pushover' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Neue Buchung zu prüfen: E-Mail' })).not.toBeChecked()
  })

  it('shows a mandatory switch on and disabled', async () => {
    setup(settings())
    const mail = await screen.findByRole('checkbox', { name: 'Neue Anmeldung: E-Mail' })
    expect(mail).toBeChecked()
    expect(mail).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'Neue Anmeldung: Pushover' })).toBeEnabled()
  })

  it('saves a switch at once', async () => {
    const sent: { url: string; body: unknown }[] = []
    setup(settings(), {
      'PUT /api/notifications/vacationrental.arrivals/pushover': (init) => {
        sent.push({ url: 'arrivals/pushover', body: JSON.parse(String(init?.body)) })
        return { status: 204 }
      },
      'PUT /api/notifications/vacationrental.bookingReview/pushover': (init) => {
        sent.push({ url: 'bookingReview/pushover', body: JSON.parse(String(init?.body)) })
        return { status: 204 }
      },
    })
    const user = userEvent.setup()
    const arrivals = await screen.findByRole('checkbox', { name: 'Anreisen morgen: Pushover' })
    await user.click(arrivals)
    await waitFor(() => expect(arrivals).toBeChecked())
    await user.click(screen.getByRole('checkbox', { name: 'Neue Buchung zu prüfen: Pushover' }))
    await waitFor(() =>
      expect(sent).toEqual([
        { url: 'arrivals/pushover', body: { enabled: true } },
        { url: 'bookingReview/pushover', body: { enabled: false } },
      ]),
    )
  })

  it('disables the switches of a channel that is not usable and says what is missing', async () => {
    setup(
      settings({
        channels: [
          { id: 'mail', label: 'E-Mail', usable: false, hint: 'Keine E-Mail-Adresse hinterlegt' },
          { id: 'pushover', label: 'Pushover', usable: true, hint: null },
        ],
      }),
    )
    expect(await screen.findByText('E-Mail: Keine E-Mail-Adresse hinterlegt')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Anreisen morgen: E-Mail' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'Anreisen morgen: Pushover' })).toBeEnabled()
    expect(screen.queryByText(/^Pushover:/)).not.toBeInTheDocument()
  })

  it('puts the stored state back and shows the message when saving fails', async () => {
    let loads = 0
    setup(settings(), {
      'GET /api/notifications': () => {
        loads += 1
        return { body: settings() }
      },
      'PUT /api/notifications/vacationrental.arrivals/mail': { status: 400, body: { detail: 'Die Einstellung ist ungültig.' } },
    })
    const user = userEvent.setup()
    const arrivals = await screen.findByRole('checkbox', { name: 'Anreisen morgen: E-Mail' })
    await user.click(arrivals)
    expect(await screen.findByText('Die Einstellung ist ungültig.')).toBeInTheDocument()
    await waitFor(() => expect(loads).toBe(2))
    await waitFor(() => expect(arrivals).not.toBeChecked())
  })
})
