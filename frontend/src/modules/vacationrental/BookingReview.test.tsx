import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import { booking, bookingPage, waldeck } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [waldeck] },
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
    await waitFor(() => expect(screen.getByLabelText('Abreise')).toHaveValue('09.11.2026'))
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
    expect(screen.getByText('Stornierung gemeldet')).toBeInTheDocument()
    // A confirmed booking keeps its two decisions.
    expect(screen.getByRole('button', { name: 'Verwerfen' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Buchung verwerfen' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Stornierung übernehmen' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Stornierung aufheben' })).toBeInTheDocument())
  })

  const reported = () =>
    booking({
      reviewState: 'NEW',
      proposal: { type: 'CANCELLATION', arrival: null, departure: null, adults: null, children: null, pets: null, expectedRent: null, guest: null },
      sources: [
        { id: 1, kind: 'MAIL', label: 'Fewo & Meer Stornierung', receivedAt: '2026-11-05T17:00:00Z' },
        { id: 2, kind: 'MAIL', label: 'Fewo & Meer Last-Minute', receivedAt: '2026-11-06T11:20:00Z' },
      ],
    })
  const sourceTexts = {
    'GET /api/vacationrental/bookings/500/sources/1': {
      body: { id: 1, label: 'Fewo & Meer Stornierung', sender: 'buchung@example.org', receivedAt: '2026-11-05T17:00:00Z', subject: 'Stornierung 250905', text: 'Ihre Buchung Nr. 250905 wurde storniert.' },
    },
    'GET /api/vacationrental/bookings/500/sources/2': {
      body: { id: 2, label: 'Fewo & Meer Last-Minute', sender: 'buchung@example.org', receivedAt: '2026-11-06T11:20:00Z', subject: 'Last-Minute Buchung 250905', text: 'Kurzfristige Anreise.' },
    },
  }

  it('reports the cancellation of a booking that is not confirmed yet and applies it', async () => {
    let current = reported()
    mockApi({
      ...base,
      ...sourceTexts,
      'GET /api/vacationrental/bookings': () => ({ body: bookingPage([current]) }),
      'GET /api/vacationrental/bookings/500': () => ({ body: current }),
      'POST /api/vacationrental/bookings/500/proposal/apply': () => {
        current = { ...current, status: 'CANCELLED', reviewState: 'NONE', proposal: null, version: current.version + 1 }
        return { body: current }
      },
    })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    const row = await screen.findByRole('row', { name: /Buchung 250905/ })
    expect(within(row).getByText('zu prüfen')).toBeInTheDocument()
    expect(within(row).getByText('Stornierung gemeldet')).toBeInTheDocument()
    await user.click(row)

    expect(await screen.findByText('Stornierung per E-Mail – bitte prüfen')).toBeInTheDocument()
    expect(screen.getByText('Der Vermittler meldet, dass diese Buchung storniert wurde. Die Buchung ist noch nicht bestätigt.')).toBeInTheDocument()
    expect(screen.queryByText('Aus E-Mail übernommen – bitte prüfen')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bestätigen' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Buchung verwerfen' })).toBeInTheDocument()
    expect(screen.getByText('Stornierung gemeldet')).toBeInTheDocument()
    // The cancellation mail is shown, although it is not the latest source.
    expect(await screen.findByText('Ihre Buchung Nr. 250905 wurde storniert.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Stornierung übernehmen' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Stornierung aufheben' })).toBeInTheDocument())
    expect(screen.queryByText('Stornierung gemeldet')).not.toBeInTheDocument()
    expect(screen.queryByText('zu prüfen')).not.toBeInTheDocument()
  })

  it('discards a reported cancellation and keeps the booking to review', async () => {
    let current = reported()
    mockApi({
      ...base,
      ...sourceTexts,
      'GET /api/vacationrental/bookings/500': () => ({ body: current }),
      'POST /api/vacationrental/bookings/500/proposal/discard': () => {
        current = { ...current, proposal: null }
        return { body: current }
      },
    })
    renderApp('/ferienvermietung/buchungen/500')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Stornierung verwerfen' }))
    expect(await screen.findByText('Aus E-Mail übernommen – bitte prüfen')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bestätigen' })).toBeInTheDocument()
    expect(screen.getByText('zu prüfen')).toBeInTheDocument()
    expect(screen.queryByText('Stornierung gemeldet')).not.toBeInTheDocument()
  })

  it('discards the whole booking from the cancellation notice', async () => {
    let discarded = false
    mockApi({
      ...base,
      ...sourceTexts,
      'GET /api/vacationrental/bookings': () => ({ body: bookingPage(discarded ? [] : [reported()]) }),
      'GET /api/vacationrental/bookings/500': { body: reported() },
      'POST /api/vacationrental/bookings/500/discard': () => {
        discarded = true
        return { status: 204 }
      },
    })
    const { router } = renderApp('/ferienvermietung/buchungen/500')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Buchung verwerfen' }))
    await waitFor(() => expect(discarded).toBe(true))
    await waitFor(() => expect(router.state.location.pathname).toBe('/ferienvermietung/buchungen'))
  })

  it('filters the list to bookings awaiting review', async () => {
    const fetch = mockApi({ ...base, 'GET /api/vacationrental/bookings': { body: bookingPage([]) } })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Nur zu prüfen' }))
    await waitFor(() => expect(fetch.mock.calls.map((c) => String(c[0]))).toContainEqual(expect.stringContaining('review=true')))
  })
})
