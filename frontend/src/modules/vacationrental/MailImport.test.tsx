import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { InboxItem, MailboxSettings } from './api'
import { booking, bookingPage, seeufer } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [seeufer] },
  'GET /api/documents': { body: [] },
}

const mailbox: MailboxSettings = {
  configured: true,
  host: 'imap.example.org',
  port: 993,
  username: 'anna',
  hasPassword: true,
  folder: 'Buchungen',
  useTls: true,
  cutoffDate: '2026-11-01',
  enabled: true,
  lastPollAt: '2026-11-07T10:00:00Z',
  lastError: null,
  aiEnabled: false,
}

function inboxItem(overrides: Partial<InboxItem> = {}): InboxItem {
  return {
    id: 31,
    receivedAt: '2026-11-07T08:00:00Z',
    sender: 'buchung@fewoundmeer.de',
    subject: 'Neue Buchung für Haus Seeufer - Nordhorn',
    agency: 'Fewo & Meer',
    mailType: 'CONFIRMATION',
    propertyName: 'Haus Seeufer - Nordhorn',
    bookingRef: '275031',
    parsedBy: 'RULE',
    result: 'UNASSIGNED_UNIT',
    resultDetail: 'Einheit für „Haus Seeufer - Nordhorn“ zuordnen',
    bookingId: null,
    ...overrides,
  }
}

describe('Review of imported bookings', () => {
  it('confirms a booking taken from a mail', async () => {
    const imported = booking({ reviewState: 'NEW', agency: 'Fewo & Meer' })
    let confirmed = false
    mockApi({
      ...base,
      'GET /api/vacationrental/bookings': { body: bookingPage([imported]) },
      'POST /api/vacationrental/bookings/500/confirm': () => {
        confirmed = true
        return { body: { ...imported, reviewState: 'NONE' } }
      },
    })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    const row = await screen.findByRole('row', { name: /Buchung 275505/ })
    expect(within(row).getByText('zu prüfen')).toBeInTheDocument()
    expect(within(row).getByText('Fewo & Meer')).toBeInTheDocument()
    await user.click(row)
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Aus E-Mail übernommen – bitte prüfen')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Bestätigen' }))
    await waitFor(() => expect(confirmed).toBe(true))
    await waitFor(() => expect(within(dialog).queryByText('Aus E-Mail übernommen – bitte prüfen')).not.toBeInTheDocument())
  })

  it('discards a booking taken from a mail', async () => {
    let discarded = false
    mockApi({
      ...base,
      'GET /api/vacationrental/bookings': { body: bookingPage([booking({ reviewState: 'NEW' })]) },
      'POST /api/vacationrental/bookings/500/discard': () => {
        discarded = true
        return { status: 204 }
      },
    })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Buchung 275505/ }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Verwerfen' }))
    await waitFor(() => expect(discarded).toBe(true))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('shows a proposed change and applies a cancellation', async () => {
    const changed = booking({
      reviewState: 'CHANGE',
      proposal: { type: 'CHANGE', arrival: null, departure: '2026-11-09', adults: null, children: null, pets: null, expectedRent: null, guest: null },
    })
    let applied = false
    mockApi({
      ...base,
      'GET /api/vacationrental/bookings': { body: bookingPage([changed]) },
      'POST /api/vacationrental/bookings/500/proposal/apply': () => {
        applied = true
        return { body: { ...changed, departure: '2026-11-09', reviewState: 'NONE', proposal: null } }
      },
    })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Buchung 275505/ }))
    const dialog = await screen.findByRole('dialog')
    const diff = within(dialog).getByRole('table', { name: 'Vorgeschlagene Änderung' })
    expect(within(diff).getByText('08.11.2026')).toBeInTheDocument()
    expect(within(diff).getByText('09.11.2026')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Übernehmen' }))
    await waitFor(() => expect(applied).toBe(true))
    expect(await within(dialog).findByLabelText('Abreise')).toHaveValue('2026-11-09')
  })

  it('applies a proposed cancellation', async () => {
    let applied = false
    const cancelled = booking({
      reviewState: 'CHANGE',
      proposal: { type: 'CANCELLATION', arrival: null, departure: null, adults: null, children: null, pets: null, expectedRent: null, guest: null },
    })
    mockApi({
      ...base,
      'GET /api/vacationrental/bookings': { body: bookingPage([cancelled]) },
      'POST /api/vacationrental/bookings/500/proposal/apply': () => {
        applied = true
        return { body: { ...cancelled, status: 'CANCELLED', reviewState: 'NONE', proposal: null } }
      },
    })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Buchung 275505/ }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Stornierung per E-Mail – bitte prüfen')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Stornierung übernehmen' }))
    await waitFor(() => expect(applied).toBe(true))
  })

  it('filters the list to bookings awaiting review', async () => {
    const fetch = mockApi({ ...base, 'GET /api/vacationrental/bookings': { body: bookingPage([]) } })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Nur zu prüfen' }))
    await waitFor(() => expect(fetch.mock.calls.map((c) => String(c[0]))).toContainEqual(expect.stringContaining('review=true')))
  })
})

describe('Inbox', () => {
  it('saves the mailbox without showing the password', async () => {
    let sent: Record<string, unknown> | undefined
    mockApi({
      ...base,
      'GET /api/vacationrental/mailbox': { body: mailbox },
      'GET /api/vacationrental/inbox': { body: [] },
      'PUT /api/vacationrental/mailbox': (init) => {
        sent = JSON.parse(String(init?.body))
        return { body: mailbox }
      },
    })
    renderApp('/ferienvermietung/posteingang')
    const user = userEvent.setup()
    const password = await screen.findByLabelText('Passwort')
    expect(password).toHaveValue('')
    expect(password).toHaveAttribute('placeholder', expect.stringContaining('gespeichert'))
    expect(screen.getByText(/KI ist ausgeschaltet/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(sent).toMatchObject({ host: 'imap.example.org', folder: 'Buchungen', password: '', cutoffDate: '2026-11-01' }))
  })

  it('shows the AI notice when AI is active', async () => {
    mockApi({
      ...base,
      'GET /api/vacationrental/mailbox': { body: { ...mailbox, aiEnabled: true } },
      'GET /api/vacationrental/inbox': { body: [] },
    })
    renderApp('/ferienvermietung/posteingang')
    expect(await screen.findByText(/samt Gastdaten an Anthropic/)).toBeInTheDocument()
  })

  it('assigns a unit to a waiting mail', async () => {
    let assigned: unknown
    mockApi({
      ...base,
      'GET /api/vacationrental/mailbox': { body: mailbox },
      'GET /api/vacationrental/inbox': { body: [inboxItem()] },
      'GET /api/vacationrental/inbox/31/text': { body: { id: 31, subject: 'x', text: 'Buchungsnummer 275031' } },
      'POST /api/vacationrental/inbox/31/assign': (init) => {
        assigned = JSON.parse(String(init?.body))
        return { body: inboxItem({ result: 'BOOKING_CREATED', bookingId: 9 }) }
      },
    })
    renderApp('/ferienvermietung/posteingang')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Neue Buchung/ }))
    const dialog = await screen.findByRole('dialog')
    expect(await within(dialog).findByText('Buchungsnummer 275031')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('combobox', { name: /Einheit für/ }))
    await user.click(await screen.findByRole('option', { name: 'Haus Seeufer' }))
    await user.click(within(dialog).getByRole('button', { name: 'Zuordnen' }))
    await waitFor(() => expect(assigned).toEqual({ unitId: 70 }))
  })

  it('retries a mail that was not recognised', async () => {
    let retried = false
    mockApi({
      ...base,
      'GET /api/vacationrental/mailbox': { body: mailbox },
      'GET /api/vacationrental/inbox': { body: [inboxItem({ result: 'NOT_RECOGNISED', subject: 'Umbuchung 275505', mailType: null })] },
      'GET /api/vacationrental/inbox/31/text': { body: { id: 31, subject: 'x', text: '...' } },
      'POST /api/vacationrental/inbox/31/retry': () => {
        retried = true
        return { body: inboxItem({ result: 'NOT_RECOGNISED' }) }
      },
    })
    renderApp('/ferienvermietung/posteingang')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Umbuchung/ }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Erneut verarbeiten' }))
    await waitFor(() => expect(retried).toBe(true))
  })
})
