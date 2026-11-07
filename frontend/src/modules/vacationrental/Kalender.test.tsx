import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive, type MockResponse } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { Calendar, CalendarBooking } from './api'
import { booking, calendar, seeufer } from './fixtures'
import { shiftMonth } from './calendar'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [seeufer] },
}

const URL = '/api/vacationrental/occupancy/calendar?propertyId=7&month='

/** One mock per month from 2026-08 to 2027-06; bookings by month. */
function months(bookings: Record<string, CalendarBooking[]> = {}, units = ['Haus Seeufer']) {
  const handlers: Record<string, () => MockResponse> = {}
  for (let m = '2026-08'; m <= '2027-06'; m = shiftMonth(m, 1)) {
    const month = m
    handlers[`GET ${URL}${month}`] = () => ({
      body: calendar({
        month,
        units: units.map((name, i) => ({ id: 70 + i, name, archived: false, canEdit: true, bookings: bookings[month] ?? [] })),
      }) satisfies Calendar,
    })
  }
  return handlers
}

function stay(id: number, arrival: string, departure: string, guestName: string, status: CalendarBooking['status'] = 'PLANNED'): CalendarBooking {
  return { id, arrival, departure, status, guestName, externalRef: null, reviewState: 'NONE' }
}

const headings = () => screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)

describe('Calendar over several months', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the start month and the two after it', async () => {
    mockApi({ ...base, ...months({ '2026-11': [stay(1, '2026-11-06', '2026-11-08', 'Gast Geplant'), stay(2, '2026-11-10', '2026-11-12', 'Gast Abgerechnet', 'SETTLED'), stay(3, '2026-11-20', '2026-11-22', 'Gast Storniert', 'CANCELLED')] }) })
    renderApp('/ferienvermietung/kalender?propertyId=7&month=2026-11')
    await waitFor(() => expect(headings()).toEqual(['November 2026', 'Dezember 2026', 'Januar 2027']))
    const november = screen.getByRole('region', { name: 'November 2026' })
    expect(within(november).getByRole('button', { name: 'Gast Geplant, Geplant' })).toHaveAttribute('data-status', 'PLANNED')
    expect(within(november).getByRole('button', { name: 'Gast Abgerechnet, Abgerechnet' })).toHaveAttribute('data-status', 'SETTLED')
    expect(screen.queryByText('Gast Storniert')).not.toBeInTheDocument()
  })

  it('loads later and earlier months with the buttons', async () => {
    mockApi({ ...base, ...months() })
    renderApp('/ferienvermietung/kalender?propertyId=7&month=2026-11')
    const user = userEvent.setup()
    await waitFor(() => expect(headings()).toHaveLength(3))
    await user.click(screen.getByRole('button', { name: 'Spätere Monate' }))
    await waitFor(() => expect(headings()).toContain('Februar 2027'))
    await user.click(screen.getByRole('button', { name: 'Frühere Monate' }))
    await waitFor(() => expect(headings()[0]).toBe('Oktober 2026'))
  })

  it('loads the next month when the bottom comes into view', async () => {
    const observers: { callback: IntersectionObserverCallback; element?: Element }[] = []
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        callback: IntersectionObserverCallback
        constructor(callback: IntersectionObserverCallback) {
          this.callback = callback
          observers.push({ callback })
        }
        observe(element: Element) {
          observers.find((o) => o.callback === this.callback)!.element = element
        }
        disconnect() {}
      },
    )
    mockApi({ ...base, ...months() })
    renderApp('/ferienvermietung/kalender?propertyId=7&month=2026-11')
    await waitFor(() => expect(headings()).toHaveLength(3))
    const bottom = observers.findLast((o) => o.element?.textContent?.includes('Spätere Monate'))!
    bottom.callback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver)
    await waitFor(() => expect(headings()).toContain('Februar 2027'))
  })

  it('shows a stay across the month boundary in both months', async () => {
    const across = stay(5, '2026-11-29', '2026-12-02', 'Gast Wechsel')
    mockApi({ ...base, ...months({ '2026-11': [across], '2026-12': [across] }) })
    renderApp('/ferienvermietung/kalender?propertyId=7&month=2026-11')
    await waitFor(() => expect(headings()).toHaveLength(3))
    expect(within(screen.getByRole('region', { name: 'November 2026' })).getByRole('button', { name: /Gast Wechsel/ })).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Dezember 2026' })).getByRole('button', { name: /Gast Wechsel/ })).toBeInTheDocument()
  })

  it('goes back to the current month with "Heute"', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-07T10:00:00Z'))
    mockApi({ ...base, ...months() })
    const { router } = renderApp('/ferienvermietung/kalender?propertyId=7&month=2027-03')
    const user = userEvent.setup()
    await waitFor(() => expect(headings()[0]).toBe('März 2027'))
    await user.click(screen.getByRole('button', { name: 'Heute' }))
    await waitFor(() => expect(headings()[0]).toBe('November 2026'))
    expect(router.state.location.search).not.toContain('month=')
    vi.useRealTimers()
  })

  it('shows a unit-only user one row per month', async () => {
    mockApi({ ...base, ...months({}, ['Whg. 2']) })
    renderApp('/ferienvermietung/kalender?propertyId=7&month=2026-11')
    await waitFor(() => expect(headings()).toHaveLength(3))
    expect(within(screen.getByRole('region', { name: 'November 2026' })).getAllByRole('rowheader').map((r) => r.textContent)).toEqual(['Whg. 2'])
  })

  it('shows a new booking in the shown months after saving', async () => {
    let saved = false
    const handlers = months()
    handlers[`GET ${URL}2026-12`] = () => ({
      body: calendar({
        month: '2026-12',
        units: [{ id: 70, name: 'Haus Seeufer', archived: false, canEdit: true, bookings: saved ? [stay(9, '2026-12-14', '2026-12-16', 'Neuer Gast')] : [] }],
      }),
    })
    mockApi({
      ...base,
      ...handlers,
      'POST /api/vacationrental/bookings': () => {
        saved = true
        return { status: 201, body: booking({ id: 9 }) }
      },
    })
    renderApp('/ferienvermietung/kalender?propertyId=7&month=2026-11')
    const user = userEvent.setup()
    await waitFor(() => expect(headings()).toHaveLength(3))
    await user.click(screen.getByRole('button', { name: 'Buchung' }))
    const dialog = await screen.findByRole('dialog')
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Seeufer'))
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    expect(await within(screen.getByRole('region', { name: 'Dezember 2026' })).findByRole('button', { name: 'Neuer Gast, Geplant' })).toBeInTheDocument()
  })
})
