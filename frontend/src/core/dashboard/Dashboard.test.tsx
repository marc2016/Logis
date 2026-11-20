import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { account, page, transaction } from '@/core/ledger/fixtures'
import { formatDate, todayIso } from '@/core/ledger/transactions/format'
import { formatEUR } from '@/lib/money'
import { booking, bookingPage, waldeck } from '@/modules/vacationrental/fixtures'
import type { MonthFigures } from '@/modules/vacationrental/api'
import { change, plusDays, stayEvents } from '@/modules/vacationrental/dashboard/figures'
import type { Tile } from './api'
import { move } from './catalog'

const year = Number(todayIso().slice(0, 4))
const today = todayIso()
const flat = { ...waldeck, id: 71, name: 'Whg. 1', propertyId: 8, propertyName: 'Strandhaus', propertyMultiUnit: true }

function months(occupancy = 0, settledNet = '0.00'): MonthFigures[] {
  return Array.from({ length: 12 }, (_, i) => ({ month: i + 1, nights: 0, occupancy, ownerNights: 0, settledNet, plannedRent: '0.00' }))
}

function inboxItem(id: number, result: string) {
  return {
    id, receivedAt: '2026-11-07T08:00:00Z', sender: 'agentur@example.org', subject: `Mail ${id}`, handler: null, kind: null,
    source: null, reference: null, subjectName: null, parsedBy: null, result, resultDetail: null, linkType: null, linkId: null,
    retryable: true,
  }
}

/** The API with a dashboard that keeps what the page saves; returns the saved bodies. */
function api({ tiles = null as Tile[] | null, rental = true, inbox = [] as unknown[], review = 0, units = [waldeck, flat] } = {}) {
  let stored = tiles
  const saved: (Tile[] | null)[] = []
  const fetch = mockApi({
    'GET /api/auth/me': authenticated(),
    'GET /api/modules': rental ? vacationRentalActive : { body: [] },
    'GET /api/dashboard': () => ({ body: { tiles: stored } }),
    'PUT /api/dashboard': (init) => {
      stored = JSON.parse(String(init?.body)).tiles
      saved.push(stored)
      return { body: { tiles: stored } }
    },
    'GET /api/mail/inbox': { body: inbox },
    'GET /api/mail/mailbox/fetch-status': { status: 204 },
    'GET /api/accounts': { body: [account({ id: 3, name: 'Girokonto', balance: '1200.50' }), account({ id: 4, name: 'Rücklage', balance: '300.00' })] },
    'GET /api/transactions': { body: page([transaction({ id: 1, counterparty: 'Stadtwerke', amount: '-80.00' })]) },
    'GET /api/vacationrental/units': { body: units },
    'GET /api/vacationrental/bookings?review=true&size=1': { body: { items: [], page: 0, size: 1, totalItems: review } },
    'GET /api/vacationrental/bookings': { body: bookingPage([]) },
    'GET /api/vacationrental/occupancy/overview/units': {
      body: [{ unitId: 70, unitName: 'Haus Waldeck', propertyId: 7, propertyName: 'Haus Waldeck', propertyMultiUnit: false, archived: false,
        total: { month: 0, nights: 0, occupancy: 0, ownerNights: 0, settledNet: '0.00', plannedRent: '0.00' } }],
    },
    'GET /api/vacationrental/occupancy/overview': { body: { unitId: 70, year, months: months(40, '100.00'), total: months()[0] } },
  })
  return { saved, fetch }
}

const headings = async () => (await screen.findAllByRole('heading', { level: 2 })).map((h) => h.textContent)

describe('Start page', () => {
  it('shows the default tiles until the user customises', async () => {
    api()
    renderApp('/')
    await waitFor(async () => expect(await headings()).toEqual(['Zu erledigen', 'Nächste An- und Abreisen', 'Belegung', 'Kontostände']))
  })

  it('leaves out the tiles of an inactive module', async () => {
    api({ rental: false })
    renderApp('/')
    await waitFor(async () => expect(await headings()).toEqual(['Zu erledigen', 'Kontostände']))
  })

  it('counts what needs attention and links to the lists', async () => {
    api({ inbox: [inboxItem(1, 'UNASSIGNED_UNIT'), inboxItem(2, 'UNASSIGNED_UNIT'), inboxItem(3, 'IGNORED')], review: 1 })
    renderApp('/')
    const tile = await screen.findByRole('region', { name: 'Zu erledigen' })
    const assign = await within(tile).findByRole('link', { name: /Einheit zuordnen/ })
    expect(assign).toHaveTextContent('2')
    expect(assign).toHaveAttribute('href', '/posteingang?ergebnis=UNASSIGNED_UNIT')
    const review = await within(tile).findByRole('link', { name: /Buchungen prüfen/ })
    expect(review).toHaveTextContent('1')
    expect(review).toHaveAttribute('href', '/ferienvermietung/pruefen')
    expect(within(tile).queryByText(/Nicht erkannt/)).not.toBeInTheDocument()
  })

  it('says when nothing needs attention', async () => {
    api()
    renderApp('/')
    const tile = await screen.findByRole('region', { name: 'Zu erledigen' })
    expect(await within(tile).findByText('Alles erledigt.')).toBeInTheDocument()
  })

  it('adds, moves, configures, removes and resets tiles, saving each change', async () => {
    const { saved } = api()
    renderApp('/')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Anpassen' }))

    // A second occupancy tile, for one unit.
    await user.click(screen.getByRole('button', { name: 'Kachel hinzufügen' }))
    await user.click(await screen.findByRole('button', { name: 'Belegung hinzufügen' }))
    await waitFor(() => expect(saved).toHaveLength(1))
    expect(saved[0]!.map((t) => t.type)).toEqual(['core.todo', 'vacationrental.arrivals', 'vacationrental.occupancy', 'core.balances', 'vacationrental.occupancy'])
    const settingsButtons = await screen.findAllByRole('button', { name: 'Belegung einstellen' })
    expect(settingsButtons).toHaveLength(2)
    await user.click(settingsButtons[1])
    await user.click(await screen.findByRole('combobox', { name: 'Einheit' }))
    await user.click(await screen.findByRole('option', { name: 'Strandhaus · Whg. 1' }))
    await user.click(screen.getByRole('button', { name: 'Übernehmen' }))
    await waitFor(() => expect(saved).toHaveLength(2))
    expect(saved[1]![4].settings).toEqual({ unitId: 71 })
    expect(saved[1]![2].settings).toEqual({})

    // Move „Kontostände“ up, remove „Nächste An- und Abreisen“.
    await user.click(screen.getByRole('button', { name: 'Kontostände nach oben' }))
    await waitFor(() => expect(saved).toHaveLength(3))
    expect(saved[2]!.map((t) => t.type).slice(0, 4)).toEqual(['core.todo', 'vacationrental.arrivals', 'core.balances', 'vacationrental.occupancy'])
    await user.click(screen.getByRole('button', { name: 'Nächste An- und Abreisen entfernen' }))
    await waitFor(() => expect(saved).toHaveLength(4))
    expect(saved[3]!.map((t) => t.type)).not.toContain('vacationrental.arrivals')

    // Back to the default, after confirming.
    await user.click(screen.getByRole('button', { name: 'Standard wiederherstellen' }))
    await user.click(await screen.findByRole('button', { name: 'Wiederherstellen' }))
    await waitFor(() => expect(saved).toHaveLength(5))
    expect(saved[4]).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Fertig' }))
    await waitFor(async () => expect(await headings()).toEqual(['Zu erledigen', 'Nächste An- und Abreisen', 'Belegung', 'Kontostände']))
  })

  it('keeps tiles of an inactive module when saving', async () => {
    const tiles: Tile[] = [
      { id: 'a', type: 'core.todo', settings: {} },
      { id: 'b', type: 'vacationrental.occupancy', settings: { unitId: 70 } },
      { id: 'c', type: 'core.balances', settings: {} },
    ]
    const { saved } = api({ tiles, rental: false })
    renderApp('/')
    const user = userEvent.setup()
    await waitFor(async () => expect(await headings()).toEqual(['Zu erledigen', 'Kontostände']))
    await user.click(screen.getByRole('button', { name: 'Anpassen' }))
    await user.click(screen.getByRole('button', { name: 'Kontostände nach oben' }))
    await waitFor(() => expect(saved).toHaveLength(1))
    expect(saved[0]!.map((t) => t.id)).toEqual(['c', 'b', 'a'])
  })

  it('says so when the chosen unit is no longer visible', async () => {
    api({ tiles: [{ id: 'b', type: 'vacationrental.occupancy', settings: { unitId: 99 } }] })
    renderApp('/')
    const tile = await screen.findByRole('region', { name: 'Belegung' })
    expect(await within(tile).findByText(/Nicht mehr verfügbar/)).toBeInTheDocument()
    expect(within(tile).queryByRole('figure')).not.toBeInTheDocument()
  })
})

describe('Tiles', () => {
  it('shows balances with a total, or the chosen accounts only', async () => {
    api({ tiles: [{ id: 'a', type: 'core.balances', settings: {} }, { id: 'b', type: 'core.balances', settings: { accountIds: [4, 99] } }] })
    renderApp('/')
    const [all, chosen] = await screen.findAllByRole('region', { name: 'Kontostände' })
    expect(await within(all).findByText('Girokonto')).toBeInTheDocument()
    expect(within(all).getByText('Summe').nextSibling).toHaveTextContent('1.500,50 €')
    expect(await within(chosen).findByText('Rücklage')).toBeInTheDocument()
    expect(within(chosen).queryByText('Girokonto')).not.toBeInTheDocument()
  })

  it('shows the latest transactions, or that the account is gone', async () => {
    api({ tiles: [{ id: 'a', type: 'core.transactions', settings: { count: 5 } }, { id: 'b', type: 'core.transactions', settings: { accountId: 99 } }] })
    renderApp('/')
    const [latest, gone] = await screen.findAllByRole('region', { name: 'Letzte Transaktionen' })
    expect(await within(latest).findByText('Stadtwerke')).toBeInTheDocument()
    expect(await within(gone).findByText(/Nicht mehr verfügbar/)).toBeInTheDocument()
  })

  it('lists arrivals and departures of the next days', async () => {
    const soon = plusDays(today, 2)
    const later = plusDays(today, 5)
    mockApiWithBookings([
      booking({ id: 1, arrival: soon, departure: later, guest: { ...booking().guest!, name: 'Erika Musterfrau' } }),
      booking({ id: 2, kind: 'OWNER', arrival: plusDays(today, -3), departure: soon, guest: null }),
    ])
    renderApp('/')
    const tile = await screen.findByRole('region', { name: 'Nächste An- und Abreisen' })
    const rows = await within(tile).findAllByRole('listitem')
    expect(rows.map((r) => r.textContent)).toEqual([
      `${formatDate(soon)}AbreiseEigenbelegung · Haus Waldeck`,
      `${formatDate(soon)}AnreiseErika Musterfrau · Haus Waldeck`,
      `${formatDate(later)}AbreiseErika Musterfrau · Haus Waldeck`,
    ])
  })

  it('shows the occupancy of the year and links to the evaluation', async () => {
    api({ tiles: [{ id: 'b', type: 'vacationrental.occupancy', settings: { unitId: 70 } }] })
    renderApp('/')
    const tile = await screen.findByRole('region', { name: 'Belegung' })
    expect(await within(tile).findByRole('figure', { name: `Belegung je Monat ${year}` })).toBeInTheDocument()
    expect(within(tile).getByRole('link', { name: 'Zur Auswertung' })).toHaveAttribute('href', `/ferienvermietung/auswertung?unitId=70&year=${year}`)
  })

  it('compares the income with the previous year', async () => {
    api({ tiles: [{ id: 'i', type: 'vacationrental.income', settings: {} }] })
    renderApp('/')
    const tile = await screen.findByRole('region', { name: 'Einnahmen' })
    // Both years from the same fixture: 100 € per month up to the current month.
    const month = Number(today.slice(5, 7))
    await within(tile).findByText('0 % gegenüber Vorjahr')
    const amount = formatEUR(`${month * 100}.00`)
    expect(tile.textContent!.split(amount)).toHaveLength(3)
    expect(within(tile).getByText('0 % gegenüber Vorjahr')).toBeInTheDocument()
    expect(within(tile).getByRole('link', { name: 'Zur Auswertung' })).toHaveAttribute('href', `/ferienvermietung/auswertung?unitId=all&year=${year}`)
  })
})

describe('Tile helpers', () => {
  it('sorts stay events and leaves out cancelled bookings', () => {
    const events = stayEvents([
      booking({ id: 1, arrival: '2026-12-02', departure: '2026-12-05' }),
      booking({ id: 2, arrival: '2026-11-28', departure: '2026-12-02' }),
      booking({ id: 3, arrival: '2026-12-01', departure: '2026-12-03', status: 'CANCELLED' }),
    ], '2026-12-01', '2026-12-04')
    expect(events.map((e) => `${e.date} ${e.arrival ? 'an' : 'ab'} ${e.booking.id}`)).toEqual(['2026-12-02 ab 2', '2026-12-02 an 1'])
  })

  it('computes the change against the previous year', () => {
    expect(change('120.00', '100.00')).toBe(20)
    expect(change('50.00', '100.00')).toBe(-50)
    expect(change('50.00', '0.00')).toBeNull()
  })

  it('moves among shown tiles and keeps hidden ones in place', () => {
    const all: Tile[] = ['a', 'x', 'b', 'c'].map((id) => ({ id, type: id === 'x' ? 'gone.module' : 'core.todo', settings: {} }))
    const shown = all.filter((t) => t.id !== 'x')
    expect(move(all, shown, 'b', -1).map((t) => t.id)).toEqual(['b', 'x', 'a', 'c'])
    expect(move(all, shown, 'a', -1).map((t) => t.id)).toEqual(['a', 'x', 'b', 'c'])
  })
})

function mockApiWithBookings(items: ReturnType<typeof booking>[]) {
  const { fetch } = api({ tiles: [{ id: 'a', type: 'vacationrental.arrivals', settings: { days: 7 } }] })
  const original = fetch.getMockImplementation()!
  fetch.mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.startsWith('/api/vacationrental/bookings?from=')) {
      return new Response(JSON.stringify(bookingPage(items)), { status: 200, headers: { 'Content-Type': 'application/json' } })
    }
    return original(input, init)
  })
}
