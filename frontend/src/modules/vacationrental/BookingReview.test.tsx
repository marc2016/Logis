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
    const imported = booking({ reviewState: 'NEW', agency: { id: 1, name: 'Fewo & Meer', archived: false } })
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
    const row = await screen.findByRole('row', { name: /Buchung 250905/ })
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
    await user.click(await screen.findByRole('row', { name: /Buchung 250905/ }))
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
    await user.click(await screen.findByRole('row', { name: /Buchung 250905/ }))
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
    await user.click(await screen.findByRole('row', { name: /Buchung 250905/ }))
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
