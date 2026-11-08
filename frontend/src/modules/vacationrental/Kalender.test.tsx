import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive, type MockResponse } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { Calendar, CalendarBooking } from './api'
import { booking, calendar, seeufer } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [seeufer] },
}

const URL = '/api/vacationrental/occupancy/calendar?propertyId=7&year='

/** One mock per year from 2025 to 2028; the bookings go to the first unit of every year. */
function years(bookings: CalendarBooking[] = [], units = ['Haus Seeufer']) {
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
  return { id, arrival, departure, status, guestName, externalRef: null, reviewState: 'NONE' }
}

const headings = () => screen.queryAllByRole('row').map((r) => r.getAttribute('aria-label'))
const monthRow = (name: string) => screen.getByRole('row', { name })
const whg = (id: number, name: string) => ({ ...seeufer, id, name, propertyMultiUnit: true })

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

  it('shows only the chosen unit', async () => {
    const units = { 'GET /api/vacationrental/units': { body: [whg(70, 'Whg. 1'), whg(71, 'Whg. 2')] } }
    mockApi({ ...base, ...units, ...years([stay(1, '2026-11-06', '2026-11-08', 'Gast Eins')], ['Whg. 1', 'Whg. 2']) })
    const view = renderApp('/ferienvermietung/kalender?year=2026')
    await waitFor(() => expect(headings()).toHaveLength(14))
    expect(screen.getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Seeufer · Whg. 1')
    expect(within(monthRow('November 2026')).getByRole('button', { name: /Gast Eins/ })).toBeInTheDocument()
    await act(() => view.router.navigate('/ferienvermietung/kalender?unitId=71&year=2026'))
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Seeufer · Whg. 2'))
    expect(screen.queryByRole('button', { name: /Gast Eins/ })).not.toBeInTheDocument()
  })

  it('offers a unit-only user only their unit', async () => {
    mockApi({ ...base, 'GET /api/vacationrental/units': { body: [whg(71, 'Whg. 2')] }, ...years([], ['Whg. 2']) })
    renderApp('/ferienvermietung/kalender?year=2026')
    const user = userEvent.setup()
    await waitFor(() => expect(headings()).toHaveLength(14))
    await user.click(screen.getByRole('combobox', { name: 'Einheit' }))
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['Einheit wählen', 'Haus Seeufer · Whg. 2'])
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
            units: [{ id: 70, name: 'Haus Seeufer', archived: false, canEdit: true, bookings: saved ? [stay(9, '2026-12-14', '2026-12-16', 'Neuer Gast')] : [] }],
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
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Seeufer'))
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    expect(await within(monthRow('Dezember 2026')).findByRole('button', { name: 'Neuer Gast, Geplant' })).toBeInTheDocument()
    expect(yearRequests).toBe(2)
  })
})
