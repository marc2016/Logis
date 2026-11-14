import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import { booking, bookingPage, seeufer } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [seeufer] },
  'GET /api/documents': { body: [] },
}


describe('Review of imported bookings', () => {
  it('confirms a booking taken from a mail', async () => {
    let current = booking({ reviewState: 'NEW', agency: { id: 1, name: 'Fewo & Meer', archived: false } })
    mockApi({
      ...base,
      'GET /api/vacationrental/bookings': () => ({ body: bookingPage([current]) }),
      'GET /api/vacationrental/bookings/500': () => ({ body: current }),
      'POST /api/vacationrental/bookings/500/confirm': () => {
        current = { ...current, reviewState: 'NONE' }
        return { body: current }
      },
    })
    const { router } = renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    const row = await screen.findByRole('row', { name: /Buchung 250905/ })
    expect(within(row).getByText('zu prüfen')).toBeInTheDocument()
    expect(within(row).getByText('Fewo & Meer')).toBeInTheDocument()
    await user.click(row)
    await waitFor(() => expect(router.state.location.pathname).toBe('/ferienvermietung/buchungen/500'))
    expect(await screen.findByText('Aus E-Mail übernommen – bitte prüfen')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Bestätigen' }))
    await waitFor(() => expect(screen.queryByText('Aus E-Mail übernommen – bitte prüfen')).not.toBeInTheDocument())
    expect(current.reviewState).toBe('NONE')
  })

  it('discards a booking taken from a mail and returns to the list', async () => {
    let discarded = false
    const imported = booking({ reviewState: 'NEW' })
    mockApi({
      ...base,
      'GET /api/vacationrental/bookings': () => ({ body: bookingPage(discarded ? [] : [imported]) }),
      'GET /api/vacationrental/bookings/500': { body: imported },
      'POST /api/vacationrental/bookings/500/discard': () => {
        discarded = true
        return { status: 204 }
      },
    })
    const { router } = renderApp('/ferienvermietung/buchungen?review=true')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Buchung 250905/ }))
    await user.click(await screen.findByRole('button', { name: 'Verwerfen' }))
    await waitFor(() => expect(discarded).toBe(true))
    await waitFor(() => expect(router.state.location.pathname).toBe('/ferienvermietung/buchungen'))
    expect(router.state.location.search).toBe('?review=true')
  })

  it('shows a proposed change and applies it', async () => {
    let current = booking({
      reviewState: 'CHANGE',
      proposal: { type: 'CHANGE', arrival: null, departure: '2026-11-09', adults: null, children: null, pets: null, expectedRent: null, guest: null },
    })
    mockApi({
      ...base,
      'GET /api/vacationrental/bookings/500': () => ({ body: current }),
      'POST /api/vacationrental/bookings/500/proposal/apply': () => {
        current = { ...current, departure: '2026-11-09', reviewState: 'NONE', proposal: null, version: current.version + 1 }
        return { body: current }
      },
    })
    renderApp('/ferienvermietung/buchungen/500')
    const user = userEvent.setup()
    const diff = await screen.findByRole('table', { name: 'Vorgeschlagene Änderung' })
    expect(within(diff).getByText('08.11.2026')).toBeInTheDocument()
    expect(within(diff).getByText('09.11.2026')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Übernehmen' }))
    await waitFor(() => expect(screen.getByLabelText('Abreise')).toHaveValue('2026-11-09'))
    expect(screen.queryByRole('table', { name: 'Vorgeschlagene Änderung' })).not.toBeInTheDocument()
  })

  it('applies a proposed cancellation', async () => {
    let current = booking({
      reviewState: 'CHANGE',
      proposal: { type: 'CANCELLATION', arrival: null, departure: null, adults: null, children: null, pets: null, expectedRent: null, guest: null },
    })
    mockApi({
      ...base,
      'GET /api/vacationrental/bookings/500': () => ({ body: current }),
      'POST /api/vacationrental/bookings/500/proposal/apply': () => {
        current = { ...current, status: 'CANCELLED', reviewState: 'NONE', proposal: null }
        return { body: current }
      },
    })
    renderApp('/ferienvermietung/buchungen/500')
    const user = userEvent.setup()
    expect(await screen.findByText('Stornierung per E-Mail – bitte prüfen')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Stornierung übernehmen' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Stornierung aufheben' })).toBeInTheDocument())
  })

  it('filters the list to bookings awaiting review', async () => {
    const fetch = mockApi({ ...base, 'GET /api/vacationrental/bookings': { body: bookingPage([]) } })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Nur zu prüfen' }))
    await waitFor(() => expect(fetch.mock.calls.map((c) => String(c[0]))).toContainEqual(expect.stringContaining('review=true')))
  })
})
