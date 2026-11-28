import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive, type MockResponse } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { Calendar, CalendarBooking } from './api'
import { booking, calendar, waldeck } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [waldeck] },
}

const URL = '/api/vacationrental/occupancy/calendar?propertyId=7&year='

/** One mock per year from 2025 to 2028; the bookings go to the first unit of every year. */
function years(bookings: CalendarBooking[] = [], units = ['Haus Waldeck']) {
  const handlers: Record<string, () => MockResponse> = {}
  for (const year of [2025, 2026, 2027, 2028]) {
    handlers[`GET ${URL}${year}`] = () => ({
      body: calendar({
        year,
        from: `${year - 1}-12`,
        to: `${year + 1}-01`,
        units: units.map((name, i) => ({ id: 70 + i, name, archived: false, canEdit: true, bookings: i === 0 ? bookings : [] })),
      }) satisfies Calendar,
    })
  }
  return handlers
}

function stay(id: number, arrival: string, departure: string, guestName: string, status: CalendarBooking['status'] = 'PLANNED'): CalendarBooking {
  return { id, arrival, departure, status, guestName, externalRef: null, reviewState: 'NONE', kind: 'GUEST', cancellationReported: false }
}

const headings = () => screen.queryAllByRole('row').map((r) => r.getAttribute('aria-label'))
const monthRow = (name: string) => screen.getByRole('row', { name })
const whg = (id: number, name: string) => ({ ...waldeck, id, name, propertyMultiUnit: true })

describe('Calendar by year', () => {
  afterEach(() => vi.useRealTimers())

  it('shows the year with December before and January after it', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-07T10:00:00Z'))
    mockApi({
      ...base,
      ...years([
        stay(1, '2026-11-06', '2026-11-08', 'Gast Geplant'),
        stay(2, '2026-11-10', '2026-11-12', 'Gast Abgerechnet', 'SETTLED'),
        stay(3, '2026-11-20', '2026-11-22', 'Gast Storniert', 'CANCELLED'),
      ]),
    })
    renderApp('/ferienvermietung/kalender')
    await waitFor(() => expect(headings()).toHaveLength(14))
    expect(headings()[0]).toBe('Dezember 2025')
    expect(headings()[1]).toBe('Januar 2026')
    expect(headings()[13]).toBe('Januar 2027')
    expect(monthRow('Dezember 2025')).toHaveAttribute('data-outside', 'true')
    expect(monthRow('Januar 2027')).toHaveAttribute('data-outside', 'true')
    expect(monthRow('Januar 2026')).not.toHaveAttribute('data-outside')
    expect(monthRow('November 2026')).toHaveAttribute('aria-current', 'date')
    const november = monthRow('November 2026')
    expect(within(november).getByRole('button', { name: 'Gast Geplant, Geplant' })).toHaveAttribute('data-status', 'PLANNED')
    expect(within(november).getByRole('button', { name: 'Gast Abgerechnet, Abgerechnet' })).toHaveAttribute('data-status', 'SETTLED')
    expect(screen.queryByText('Gast Storniert')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Spätere Monate|Frühere Monate/ })).not.toBeInTheDocument()
  })

  it('marks a stay whose cancellation a mail reports', async () => {
    const reported = { ...stay(8, '2026-11-06', '2026-11-08', 'Gast Gemeldet'), reviewState: 'NEW' as const, cancellationReported: true }
    mockApi({ ...base, ...years([reported, stay(9, '2026-11-10', '2026-11-12', 'Gast Geplant')]) })
    renderApp('/ferienvermietung/kalender?year=2026')
    const november = await screen.findByRole('row', { name: 'November 2026' })
    const bar = await within(november).findByRole('button', { name: 'Gast Gemeldet, Geplant, zu prüfen, Stornierung gemeldet' })
    expect(bar).toHaveAttribute('data-cancellation-reported', 'true')
    expect(within(november).getByRole('button', { name: 'Gast Geplant, Geplant' })).not.toHaveAttribute('data-cancellation-reported')
  })

  it('shows an owner use set apart from guest stays', async () => {
    const owner = { ...stay(7, '2026-11-09', '2026-11-14', ''), guestName: null, kind: 'OWNER' as const }
    mockApi({ ...base, ...years([owner]) })
    renderApp('/ferienvermietung/kalender?year=2026')
    const bar = await within(await screen.findByRole('row', { name: 'November 2026' })).findByRole('button', { name: 'Eigenbelegung' })
    expect(bar).toHaveAttribute('data-kind', 'OWNER')
  })

  it('shows a stay across the month and the year boundary in each month', async () => {
    const month = stay(5, '2026-11-29', '2026-12-02', 'Gast Monatswechsel')
    const year = stay(6, '2026-12-30', '2027-01-02', 'Gast Jahreswechsel')
    mockApi({ ...base, ...years([month, year]) })
    const view = renderApp('/ferienvermietung/kalender?year=2026')
    await waitFor(() => expect(headings()).toHaveLength(14))
    expect(within(monthRow('November 2026')).getByRole('button', { name: /Gast Monatswechsel/ })).toBeInTheDocument()
    expect(within(monthRow('Dezember 2026')).getByRole('button', { name: /Gast Monatswechsel/ })).toBeInTheDocument()
    expect(within(monthRow('Dezember 2026')).getByRole('button', { name: /Gast Jahreswechsel/ })).toBeInTheDocument()
    expect(within(monthRow('Januar 2027')).getByRole('button', { name: /Gast Jahreswechsel/ })).toBeInTheDocument()

    await act(() => view.router.navigate('/ferienvermietung/kalender?year=2027'))
    await waitFor(() => expect(headings()[0]).toBe('Dezember 2026'))
    expect(within(monthRow('Dezember 2026')).getByRole('button', { name: /Gast Jahreswechsel/ })).toBeInTheDocument()
    expect(within(monthRow('Januar 2027')).getByRole('button', { name: /Gast Jahreswechsel/ })).toBeInTheDocument()
  })

  it('pages to the next year and back to the current year with "Heute"', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-07T10:00:00Z'))
    const fetch = mockApi({ ...base, ...years() })
    const { router } = renderApp('/ferienvermietung/kalender')
    const user = userEvent.setup()
    await waitFor(() => expect(headings()).toHaveLength(14))
    expect(screen.getByRole('button', { name: 'Heute' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Jahr 2027' }))
    await waitFor(() => expect(headings()[0]).toBe('Dezember 2026'))
    expect(headings()[13]).toBe('Januar 2028')
    expect(router.state.location.search).toContain('year=2027')
    expect(fetch.mock.calls.map((c) => String(c[0]))).toContain(`${URL}2027`)

    await user.click(screen.getByRole('button', { name: 'Heute' }))
    await waitFor(() => expect(headings()[0]).toBe('Dezember 2025'))
    expect(router.state.location.search).not.toContain('year=')
  })

  it('shows the weekday of every day and marks today', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-20T10:00:00Z'))
    mockApi({ ...base, ...years() })
    renderApp('/ferienvermietung/kalender')
    await waitFor(() => expect(headings()).toHaveLength(14))
    const friday = within(monthRow('November 2026')).getByRole('gridcell', { name: 'Freitag, 20. November 2026, heute' })
    expect(friday).toHaveTextContent('20Fr')
    expect(friday).toHaveAttribute('aria-current', 'date')
    expect(within(monthRow('November 2026')).getByRole('gridcell', { name: 'Samstag, 21. November 2026' })).toHaveTextContent('21Sa')
    // Exactly one day is today; the same day of the neighbouring years is not.
    expect(document.querySelectorAll('[data-today]')).toHaveLength(1)
  })

  it('fits the width instead of scrolling sideways', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-28T10:00:00Z'))
    mockApi({ ...base, ...years() })
    renderApp('/ferienvermietung/kalender')
    await waitFor(() => expect(headings()).toHaveLength(14))
    const frame = screen.getByTestId('calendar-frame')
    expect(frame.className).not.toMatch(/overflow-x-auto/)
    expect(frame.className).toMatch(/@container\/calendar/)
    // Day columns may shrink to any width; the month column narrows by a container query (design D1, D2).
    expect(screen.getByRole('grid', { name: 'Belegung' }).style.gridTemplateColumns).toBe('var(--month-column) repeat(31, minmax(0, 1fr))')
  })

  it('shows the short month and only the number on narrow calendars, keeping the full names for screen readers', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-28T10:00:00Z'))
    mockApi({ ...base, ...years() })
    renderApp('/ferienvermietung/kalender')
    await waitFor(() => expect(headings()).toHaveLength(14))
    const november = monthRow('November 2026')
    const header = within(november).getByRole('rowheader', { name: 'November 2026' })
    expect(header).toHaveTextContent('Nov 26')
    expect(header).toHaveTextContent('Nov. 2026')
    expect(within(header).getByText('Nov 26').className).toMatch(/@min-\[46rem\]\/calendar:hidden/)
    expect(within(header).getByText('Nov. 2026').className).toMatch(/^hidden /)
    expect(within(monthRow('März 2026')).getByRole('rowheader')).toHaveTextContent('Mär 26')
    // The weekday is hidden on narrow calendars; the cell's name still says it.
    const today = within(november).getByRole('gridcell', { name: 'Samstag, 28. November 2026, heute' })
    const weekday = today.querySelector('[data-weekday]')!
    expect(weekday).toHaveTextContent('Sa')
    expect(weekday.className).toMatch(/^hidden .*@min-\[46rem\]\/calendar:inline/)
    expect(today.className).toMatch(/bg-muted\/50/)
  })

  it('cuts off the label of a short bar but keeps its name and opening it', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-28T10:00:00Z'))
    mockApi({
      ...base,
      ...years([stay(520, '2026-11-10', '2026-11-12', 'Erika Musterfrau-Beispiel')]),
    })
    renderApp('/ferienvermietung/kalender')
    const bar = await within(await waitFor(() => monthRow('November 2026'))).findByRole('button', { name: /Erika Musterfrau-Beispiel/ })
    expect(bar.className).toMatch(/truncate/)
    expect(bar).toHaveAttribute('title', expect.stringContaining('Erika Musterfrau-Beispiel'))
  })

  it('opens the year of an old month link', async () => {
    mockApi({ ...base, ...years() })
    const { router } = renderApp('/ferienvermietung/kalender?month=2028-03')
    await waitFor(() => expect(headings()[0]).toBe('Dezember 2027'))
    expect(headings()[13]).toBe('Januar 2029')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Jahr 2027' }))
    await waitFor(() => expect(router.state.location.search).toContain('year=2027'))
    expect(router.state.location.search).not.toContain('month=')
  })

  it('opens a booking on its own page and returns to the year', async () => {
    mockApi({ ...base, ...years([stay(1, '2026-11-06', '2026-11-08', 'Gast Geplant')]), 'GET /api/vacationrental/bookings/1': { body: booking({ id: 1 }) },
      'GET /api/vacationrental/agencies': { body: [] }, 'GET /api/documents': { body: [] } })
    const { router } = renderApp('/ferienvermietung/kalender?year=2026')
    const user = userEvent.setup()
    await user.click(await within(await screen.findByRole('row', { name: 'November 2026' })).findByRole('button', { name: 'Gast Geplant, Geplant' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/ferienvermietung/buchungen/1'))
    expect(await screen.findByRole('link', { name: 'Zurück zum Kalender' })).toHaveAttribute('href', '/ferienvermietung/kalender?year=2026')
  })

  it('shows only the chosen unit', async () => {
    const units = { 'GET /api/vacationrental/units': { body: [whg(70, 'Whg. 1'), whg(71, 'Whg. 2')] } }
    mockApi({ ...base, ...units, ...years([stay(1, '2026-11-06', '2026-11-08', 'Gast Eins')], ['Whg. 1', 'Whg. 2']) })
    const view = renderApp('/ferienvermietung/kalender?year=2026')
    await waitFor(() => expect(headings()).toHaveLength(14))
    expect(screen.getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Waldeck · Whg. 1')
    expect(within(monthRow('November 2026')).getByRole('button', { name: /Gast Eins/ })).toBeInTheDocument()
    await act(() => view.router.navigate('/ferienvermietung/kalender?unitId=71&year=2026'))
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Waldeck · Whg. 2'))
    expect(screen.queryByRole('button', { name: /Gast Eins/ })).not.toBeInTheDocument()
  })

  it('offers a unit-only user only their unit', async () => {
    mockApi({ ...base, 'GET /api/vacationrental/units': { body: [whg(71, 'Whg. 2')] }, ...years([], ['Whg. 2']) })
    renderApp('/ferienvermietung/kalender?year=2026')
    const user = userEvent.setup()
    await waitFor(() => expect(headings()).toHaveLength(14))
    await user.click(screen.getByRole('combobox', { name: 'Einheit' }))
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['Einheit wählen', 'Haus Waldeck · Whg. 2'])
  })

  it('shows a new booking after saving with one request for the year', async () => {
    let saved = false
    let yearRequests = 0
    mockApi({
      ...base,
      [`GET ${URL}2026`]: () => {
        yearRequests++
        return {
          body: calendar({
            units: [{ id: 70, name: 'Haus Waldeck', archived: false, canEdit: true, bookings: saved ? [stay(9, '2026-12-14', '2026-12-16', 'Neuer Gast')] : [] }],
          }),
        }
      },
      'POST /api/vacationrental/bookings': () => {
        saved = true
        return { status: 201, body: booking({ id: 9 }) }
      },
    })
    renderApp('/ferienvermietung/kalender?year=2026')
    const user = userEvent.setup()
    await waitFor(() => expect(headings()).toHaveLength(14))
    expect(yearRequests).toBe(1)
    await user.click(screen.getByRole('button', { name: 'Buchung' }))
    const dialog = await screen.findByRole('dialog')
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Waldeck'))
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    expect(await within(monthRow('Dezember 2026')).findByRole('button', { name: 'Neuer Gast, Geplant' })).toBeInTheDocument()
    expect(yearRequests).toBe(2)
  })
})
