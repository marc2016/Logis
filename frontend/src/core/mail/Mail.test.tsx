import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import { booking, statement, waldeck } from '@/modules/vacationrental/fixtures'
import type { FetchStatus, InboxItem, MailboxSettings } from './api'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [waldeck] },
  'GET /api/mail/mailbox/fetch-status': { status: 204 },
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
  adminExportAllowed: false,
  lastExport: null,
}

function inboxItem(overrides: Partial<InboxItem> = {}): InboxItem {
  return {
    id: 31,
    receivedAt: '2026-11-07T08:00:00Z',
    sender: 'buchung@fewoundmeer.de',
    subject: 'Neue Buchung für Haus Waldeck - Talstadt',
    handler: 'vacationrental.booking',
    kind: 'CONFIRMATION',
    source: 'Fewo & Meer',
    reference: '250903',
    subjectName: 'Haus Waldeck - Talstadt',
    parsedBy: 'RULE',
    result: 'UNASSIGNED_UNIT',
    resultDetail: 'Einheit für „Haus Waldeck - Talstadt“ zuordnen',
    linkType: null,
    linkId: null,
    retryable: true,
    ...overrides,
    // As the server reports it: what is left to do for the user.
    needsAttention:
      overrides.needsAttention ??
      ['NOT_RECOGNISED', 'FAILED', 'UNASSIGNED_UNIT', 'NOT_MATCHED', 'PERIOD_TAKEN'].includes(overrides.result ?? 'UNASSIGNED_UNIT'),
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

  it('switches the consent to the admin export and shows the last export', async () => {
    let current: MailboxSettings = mailbox
    const sent: unknown[] = []
    mockApi({
      ...base,
      'GET /api/mail/mailbox': () => ({ body: current }),
      'GET /api/mail/inbox': { body: [] },
      'PUT /api/mail/mailbox/export-consent': (init) => {
        const body = JSON.parse(String(init?.body))
        sent.push(body)
        current = { ...current, adminExportAllowed: body.allowed }
        return { body: current }
      },
    })
    renderApp('/posteingang')
    const user = userEvent.setup()
    const consent = await screen.findByRole('checkbox', { name: 'Admins dürfen meine Mails pseudonymisiert exportieren' })
    expect(consent).not.toBeChecked()
    expect(screen.queryByText(/Zuletzt exportiert/)).not.toBeInTheDocument()

    await user.click(consent)
    await waitFor(() => expect(consent).toBeChecked())
    expect(sent).toEqual([{ allowed: true }])
  })

  it('shows the last admin export', async () => {
    mockApi({
      ...base,
      'GET /api/mail/mailbox': {
        body: { ...mailbox, adminExportAllowed: true, lastExport: { at: '2026-11-19T09:30:00Z', mailCount: 42, adminName: 'Anna Admin' } },
      },
      'GET /api/mail/inbox': { body: [] },
    })
    renderApp('/posteingang')
    expect(await screen.findByText(/Zuletzt exportiert am .* · 42 Mails · von Anna Admin/)).toBeInTheDocument()
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
      'GET /api/mail/inbox/31/text': { body: { id: 31, subject: 'x', text: 'Buchungsnummer 250903' } },
      'POST /api/vacationrental/mail/31/assign': (init) => {
        assigned = JSON.parse(String(init?.body))
        return { status: 204 }
      },
    })
    renderApp('/posteingang')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Neue Buchung/ }))
    const dialog = await screen.findByRole('dialog')
    expect(await within(dialog).findByText('Buchungsnummer 250903')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('combobox', { name: /Einheit für/ }))
    await user.click(await screen.findByRole('option', { name: 'Haus Waldeck' }))
    await user.click(within(dialog).getByRole('button', { name: 'Zuordnen' }))
    await waitFor(() => expect(assigned).toEqual({ unitId: 70 }))
  })

  it('opens the booking a mail created on its own page', async () => {
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': { body: [inboxItem({ result: 'BOOKING_CREATED', resultDetail: null, linkType: 'vacationrental.booking', linkId: 500 })] },
      'GET /api/mail/inbox/31/text': { body: { id: 31, subject: 'x', text: 'Buchungsnummer 250903' } },
      'GET /api/vacationrental/bookings/500': { body: booking() },
      'GET /api/vacationrental/agencies': { body: [] },
      'GET /api/documents': { body: [] },
    })
    const { router } = renderApp('/posteingang')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Neue Buchung/ }))
    await user.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: 'Zur Buchung' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/ferienvermietung/buchungen/500'))
    expect(await screen.findByRole('link', { name: 'Zurück zum Posteingang' })).toHaveAttribute('href', '/posteingang')
  })

  it('retries a mail that was not recognised', async () => {
    let retried = false
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': { body: [inboxItem({ result: 'NOT_RECOGNISED', subject: 'Umbuchung 250905', handler: null, kind: null, retryable: true })] },
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

  describe('bulk actions', () => {
    const toolbar = () => screen.getByRole('toolbar', { name: 'Aktionen für die Auswahl' })

    it('ignores and deletes selected mails, and reports mails that cannot be retried', async () => {
      let items = [
        inboxItem({ id: 31, subject: 'Mail A', result: 'NOT_RECOGNISED', handler: null, retryable: true }),
        inboxItem({ id: 32, subject: 'Mail B', result: 'IGNORED', handler: null, retryable: false }),
      ]
      const retried: number[] = []
      mockApi({
        ...base,
        'GET /api/mail/mailbox': { body: mailbox },
        'GET /api/mail/inbox': () => ({ body: items }),
        'POST /api/mail/inbox/31/retry': () => {
          retried.push(31)
          return { body: items[0] }
        },
        'DELETE /api/mail/inbox/31': () => {
          items = items.filter((m) => m.id !== 31)
          return { status: 204 }
        },
        'DELETE /api/mail/inbox/32': () => {
          items = items.filter((m) => m.id !== 32)
          return { status: 204 }
        },
      })
      renderApp('/posteingang')
      const user = userEvent.setup()
      await user.click(await screen.findByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
      await user.click(within(toolbar()).getByRole('button', { name: 'Erneut verarbeiten' }))
      expect(await screen.findByText('1 erneut verarbeitet, 1 nicht: Die Mail kann nicht erneut verarbeitet werden')).toBeInTheDocument()
      expect(retried).toEqual([31])

      // The mail that could not be retried stays selected; select the whole page again.
      expect(within(toolbar()).getByText('1 ausgewählt')).toBeInTheDocument()
      await user.click(screen.getByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
      await user.click(within(toolbar()).getByRole('button', { name: 'Löschen' }))
      await user.click(within(toolbar()).getByRole('button', { name: 'Ja' }))
      expect(await screen.findByText('2 gelöscht')).toBeInTheDocument()
      await waitFor(() => expect(screen.queryByRole('row', { name: /^Mail/ })).not.toBeInTheDocument())
    })

    it('assigns one unit to several waiting booking mails', async () => {
      const assigned: number[] = []
      mockApi({
        ...base,
        'GET /api/mail/mailbox': { body: mailbox },
        'GET /api/mail/inbox': {
          body: [
            inboxItem({ id: 31, subject: 'Neue Buchung 1' }),
            inboxItem({ id: 32, subject: 'Neue Buchung 2' }),
            inboxItem({ id: 33, subject: 'Neue Buchung 3', result: 'BOOKING_CREATED', resultDetail: null }),
          ],
        },
        'POST /api/vacationrental/mail/31/assign': () => (assigned.push(31), { status: 204 }),
        'POST /api/vacationrental/mail/32/assign': () => (assigned.push(32), { status: 204 }),
      })
      renderApp('/posteingang')
      const user = userEvent.setup()
      await user.click(await screen.findByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
      await user.click(within(toolbar()).getByRole('button', { name: 'Einheit zuordnen' }))
      const dialog = await screen.findByRole('dialog', { name: 'Einheit zuordnen' })
      expect(within(dialog).getByText(/2 Mails warten auf eine Einheit/)).toBeInTheDocument()
      await user.click(within(dialog).getByRole('combobox', { name: 'Einheit' }))
      await user.click(await screen.findByRole('option', { name: 'Haus Waldeck' }))
      await user.click(within(dialog).getByRole('button', { name: 'Zuordnen' }))
      expect(await screen.findByText('2 zugeordnet, 1 nicht: Die Mail wartet nicht auf eine Einheit')).toBeInTheDocument()
      expect(assigned.sort()).toEqual([31, 32])
    })
  })
})

describe('Fetching older mails', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  function today() {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 18, 12, 0))
  }

  const running: FetchStatus = {
    kind: 'OLDER',
    from: '2023-01-01',
    total: 400,
    checked: 120,
    created: 35,
    state: 'RUNNING',
    error: null,
    startedAt: '2026-11-18T11:00:00Z',
    finishedAt: null,
  }

  it('jumps back several years with a quick range', async () => {
    today()
    let sent: unknown
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': { body: [] },
      'POST /api/mail/mailbox/fetch-older': (init) => {
        sent = JSON.parse(String(init?.body))
        return { status: 202, body: { ...running, from: '2021-11-18', total: null, checked: 0, created: 0 } }
      },
    })
    renderApp('/posteingang')
    const user = userEvent.setup()
    const fetchOlder = await screen.findByRole('button', { name: 'Ältere Mails abrufen' })
    expect(fetchOlder).toBeDisabled()
    expect(screen.getByText(/Bereits abgerufene Mails werden übersprungen/)).toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: '5 Jahre' }))
    expect(screen.getByLabelText('Ältere Mails ab')).toHaveValue('18.11.2021')
    await user.click(fetchOlder)
    await waitFor(() => expect(sent).toEqual({ from: '2021-11-18' }))
    expect(await screen.findByText('Mails ab 18.11.2021: Postfach wird durchsucht …')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Jetzt abrufen' })).toBeDisabled()
  })

  it('accepts a typed date and explains invalid ones', async () => {
    today()
    mockApi({ ...base, 'GET /api/mail/mailbox': { body: mailbox }, 'GET /api/mail/inbox': { body: [] } })
    renderApp('/posteingang')
    const user = userEvent.setup()
    const field = await screen.findByLabelText('Ältere Mails ab')
    const fetchOlder = screen.getByRole('button', { name: 'Ältere Mails abrufen' })

    await user.type(field, '01.03.20')
    await user.tab()
    expect(screen.getByText('Bitte ein gültiges Datum eingeben.')).toBeInTheDocument()
    expect(fetchOlder).toBeDisabled()

    await user.type(field, '23')
    expect(field).toHaveValue('01.03.2023')
    expect(fetchOlder).toBeEnabled()
    expect(screen.queryByText('Bitte ein gültiges Datum eingeben.')).not.toBeInTheDocument()

    await user.clear(field)
    await user.type(field, '01.01.2027')
    expect(screen.getByText('Das Datum darf nicht in der Zukunft liegen.')).toBeInTheDocument()
    expect(fetchOlder).toBeDisabled()
  })

  it('picks month and year in the calendar directly', async () => {
    today()
    mockApi({ ...base, 'GET /api/mail/mailbox': { body: mailbox }, 'GET /api/mail/inbox': { body: [] } })
    renderApp('/posteingang')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Kalender öffnen: Ältere Mails ab' }))
    const dialog = await screen.findByRole('dialog')
    const [month, year] = await within(dialog).findAllByRole('combobox')
    await user.selectOptions(year, '2022')
    await user.selectOptions(month, '0')
    await user.click(within(dialog).getByRole('button', { name: /15\. Januar 2022/ }))
    expect(screen.getByLabelText('Ältere Mails ab')).toHaveValue('15.01.2022')
    expect(screen.getByRole('button', { name: 'Ältere Mails abrufen' })).toBeEnabled()
  })

  it('shows the progress of a running fetch and its result', async () => {
    let status: FetchStatus = running
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': { body: [] },
      'GET /api/mail/mailbox/fetch-status': () => ({ body: status }),
    })
    renderApp('/posteingang')
    expect(await screen.findByText('Mails ab 01.01.2023: 120 von 400 geprüft, 35 neu')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Fortschritt des Abrufs' })).toHaveAttribute('value', '120')
    expect(screen.getByRole('button', { name: 'Jetzt abrufen' })).toBeDisabled()

    status = { ...running, checked: 400, created: 90, state: 'DONE', finishedAt: '2026-11-18T11:05:00Z' }
    expect(await screen.findByText('Abruf abgeschlossen: 90 neue Mails', {}, { timeout: 4000 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Jetzt abrufen' })).toBeEnabled()
  })

  it('reports a fetch that is already running', async () => {
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': { body: [] },
      'POST /api/mail/mailbox/poll': { status: 409, body: { detail: 'Es läuft bereits ein Abruf.' } },
    })
    renderApp('/posteingang')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Jetzt abrufen' }))
    expect(await screen.findByText('Es läuft bereits ein Abruf.')).toBeInTheDocument()
  })
})

describe('Sorting and filtering the inbox', () => {
  const items = [
    inboxItem({ id: 1, subject: 'Neue Buchung Bravo', receivedAt: '2025-03-10T08:00:00Z', result: 'BOOKING_CREATED' }),
    inboxItem({ id: 2, subject: 'Alpha Storno', receivedAt: '2025-06-01T08:00:00Z', result: 'NOT_RECOGNISED', handler: null, kind: null, source: null, sender: 'info@example.org' }),
    inboxItem({ id: 3, subject: 'Charlie Anfrage', receivedAt: '2026-11-07T08:00:00Z', result: 'UNASSIGNED_UNIT' }),
    inboxItem({ id: 4, subject: 'Delta Werbung', receivedAt: '2026-11-08T08:00:00Z', result: 'IGNORED', handler: null, kind: null, source: null, sender: 'news@example.org' }),
  ]
  const subjects = () =>
    screen.getAllByRole('row', { name: /^Mail / }).map((r) => r.getAttribute('aria-label')?.replace('Mail ', ''))

  function setUp() {
    mockApi({ ...base, 'GET /api/mail/mailbox': { body: mailbox }, 'GET /api/mail/inbox': { body: items } })
  }

  it('shows the newest mails first and sorts by any column', async () => {
    setUp()
    renderApp('/posteingang')
    const user = userEvent.setup()
    await screen.findByRole('row', { name: 'Mail Delta Werbung' })
    expect(subjects()).toEqual(['Delta Werbung', 'Charlie Anfrage', 'Alpha Storno', 'Neue Buchung Bravo'])
    await user.click(screen.getByRole('button', { name: 'Betreff' }))
    expect(subjects()).toEqual(['Alpha Storno', 'Charlie Anfrage', 'Delta Werbung', 'Neue Buchung Bravo'])
    expect(screen.getByRole('columnheader', { name: 'Betreff' })).toHaveAttribute('aria-sort', 'ascending')
    await user.click(screen.getByRole('button', { name: 'Betreff' }))
    expect(subjects()).toEqual(['Neue Buchung Bravo', 'Delta Werbung', 'Charlie Anfrage', 'Alpha Storno'])
  })

  it('shows only open mails with the quick filter and combines filters', async () => {
    setUp()
    renderApp('/posteingang')
    const user = userEvent.setup()
    await screen.findByRole('row', { name: 'Mail Delta Werbung' })
    await user.click(screen.getByRole('button', { name: 'Offen' }))
    expect(subjects()).toEqual(['Charlie Anfrage', 'Alpha Storno'])

    await user.click(screen.getByRole('button', { name: 'Filter zurücksetzen' }))
    expect(subjects()).toHaveLength(4)
    await user.click(screen.getByRole('combobox', { name: 'Absender' }))
    await user.click(await screen.findByRole('option', { name: 'Fewo & Meer' }))
    await user.type(screen.getByLabelText('Eingang von'), '01.01.2025')
    await user.type(screen.getByLabelText('Eingang bis'), '31.12.2025')
    expect(subjects()).toEqual(['Neue Buchung Bravo'])

    await user.click(screen.getByRole('button', { name: 'Filter zurücksetzen' }))
    await user.type(screen.getByLabelText('Betreff'), 'storno')
    expect(subjects()).toEqual(['Alpha Storno'])
  })

  it('keeps filters and sorting in the address', async () => {
    setUp()
    renderApp('/posteingang?ergebnis=offen&sort=source,asc')
    await screen.findByRole('row', { name: 'Mail Charlie Anfrage' })
    expect(subjects()).toEqual(['Charlie Anfrage', 'Alpha Storno'])
    expect(screen.getByRole('button', { name: 'Offen' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('columnheader', { name: 'Absender' })).toHaveAttribute('aria-sort', 'ascending')
  })

  it('clears the selection when the filter changes', async () => {
    setUp()
    renderApp('/posteingang')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
    expect(screen.getByRole('toolbar', { name: 'Aktionen für die Auswahl' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Offen' }))
    expect(screen.queryByRole('toolbar', { name: 'Aktionen für die Auswahl' })).not.toBeInTheDocument()
  })
})

describe('Owner block mails', () => {
  it('shows the owner use created from an owner block notice', async () => {
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': {
        body: [inboxItem({ subject: 'Eigentümerblockung 275001, Haus Waldeck - Talstadt', kind: 'OWNER_BLOCK', result: 'OWNER_USE_CREATED', linkType: 'vacationrental.booking', linkId: 504, retryable: false })],
      },
    })
    renderApp('/posteingang')
    const row = await screen.findByRole('row', { name: /Eigentümerblockung 275001/ })
    expect(within(row).getByText('Eigenbelegung angelegt')).toBeInTheDocument()
    expect(within(row).getByText('Eigentümerblockung')).toBeInTheDocument()
  })
})

describe('Ticket receipts', () => {
  it('shows an agency ticket receipt as ignored', async () => {
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': {
        body: [inboxItem({ subject: '[#ABC-123-45678]: Eigentümerblockung', kind: 'TICKET_RECEIPT', result: 'IGNORED', source: 'Fewo & Meer', reference: 'ABC-123-45678' })],
      },
    })
    renderApp('/posteingang')
    const row = await screen.findByRole('row', { name: /ABC-123-45678/ })
    expect(within(row).getByText('Ignoriert')).toBeInTheDocument()
    expect(within(row).getByText('Eingangsbestätigung')).toBeInTheDocument()
  })
})

describe('Extension notices', () => {
  it('shows an extension notice that proposed a change', async () => {
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': {
        body: [inboxItem({ subject: '[#XYZ-111-22222]: Information über eine Verlängerungsbuchung', kind: 'EXTENSION', result: 'CHANGE_PROPOSED', source: 'Fewo & Meer', linkType: 'vacationrental.booking', linkId: 505 })],
      },
    })
    renderApp('/posteingang')
    const row = await screen.findByRole('row', { name: /Verlängerungsbuchung/ })
    expect(within(row).getByText('Änderung vorgeschlagen')).toBeInTheDocument()
    expect(within(row).getByText('Verlängerung')).toBeInTheDocument()
  })

  it('shows a statement mail and opens the statement it created', async () => {
    mockApi({
      ...base,
      'GET /api/mail/mailbox': { body: mailbox },
      'GET /api/mail/inbox': {
        body: [
          inboxItem({
            subject: 'Ihre Abrechnung für März 2027 - Haus Waldeck - Talstadt',
            kind: 'STATEMENT',
            result: 'STATEMENT_CREATED',
            resultDetail: null,
            reference: 'R-2027-03',
            linkType: 'vacationrental.statement',
            linkId: 900,
            retryable: false,
          }),
        ],
      },
      'GET /api/mail/inbox/31/text': { body: { id: 31, subject: 'x', text: 'anbei erhalten Sie die Abrechnung' } },
      'GET /api/vacationrental/statements/900': { body: statement() },
      'GET /api/documents': { body: [] },
    })
    const { router } = renderApp('/posteingang')
    const user = userEvent.setup()
    const row = await screen.findByRole('row', { name: /Ihre Abrechnung/ })
    expect(within(row).getByText('Abrechnung angelegt')).toBeInTheDocument()
    expect(within(row).getByText('Abrechnung')).toBeInTheDocument()
    await user.click(row)
    await user.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: 'Zur Abrechnung' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/ferienvermietung/abrechnungen/900'))
  })
})
