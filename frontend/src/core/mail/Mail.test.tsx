import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import { seeufer } from '@/modules/vacationrental/fixtures'
import type { InboxItem, MailboxSettings } from './api'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [seeufer] },
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
    handler: 'vacationrental.booking',
    kind: 'CONFIRMATION',
    source: 'Fewo & Meer',
    reference: '275031',
    subjectName: 'Haus Seeufer - Nordhorn',
    parsedBy: 'RULE',
    result: 'UNASSIGNED_UNIT',
    resultDetail: 'Einheit für „Haus Seeufer - Nordhorn“ zuordnen',
    linkType: null,
    linkId: null,
    retryable: true,
    ...overrides,
  }
}

describe('Inbox', () => {
  it('saves the mailbox without showing the password', async () => {
    let sent: Record<string, unknown> | undefined
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': { body: [] },
      'PUT /api/mail/mailbox': (init) => {
        sent = JSON.parse(String(init?.body))
        return { body: mailbox }
      },
    })
    renderApp('/posteingang')
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
      'GET /api/mail/mailbox': { body: { ...mailbox, aiEnabled: true } },
      'GET /api/mail/inbox': { body: [] },
    })
    renderApp('/posteingang')
    expect(await screen.findByText(/samt Gastdaten an Anthropic/)).toBeInTheDocument()
  })

  it('assigns a unit to a waiting mail', async () => {
    let assigned: unknown
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': { body: [inboxItem()] },
      'GET /api/mail/inbox/31/text': { body: { id: 31, subject: 'x', text: 'Buchungsnummer 275031' } },
      'POST /api/vacationrental/mail/31/assign': (init) => {
        assigned = JSON.parse(String(init?.body))
        return { status: 204 }
      },
    })
    renderApp('/posteingang')
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
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': { body: [inboxItem({ result: 'NOT_RECOGNISED', subject: 'Umbuchung 275505', handler: null, kind: null, retryable: true })] },
      'GET /api/mail/inbox/31/text': { body: { id: 31, subject: 'x', text: '...' } },
      'POST /api/mail/inbox/31/retry': () => {
        retried = true
        return { body: inboxItem({ result: 'NOT_RECOGNISED', retryable: true }) }
      },
    })
    renderApp('/posteingang')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Umbuchung/ }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Erneut verarbeiten' }))
    await waitFor(() => expect(retried).toBe(true))
  })


  it('shows module labels and "Posteingang" in the sidebar', async () => {
    mockApi({ ...base, 'GET /api/mail/mailbox': { body: mailbox }, 'GET /api/mail/inbox': { body: [inboxItem()] } })
    renderApp('/posteingang')
    const row = await screen.findByRole('row', { name: /Neue Buchung/ })
    expect(within(row).getByText('Einheit zuordnen')).toBeInTheDocument()
    expect(within(row).getByText('Bestätigung')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Posteingang/ })).toHaveAttribute('href', '/posteingang')
  })
})
