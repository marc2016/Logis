import { describe, expect, it } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { Booking } from './api'
import { booking, bookingPage, waldeck } from './fixtures'
import { nextAfter, previousBefore } from './reviewQueue'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [waldeck] },
  'GET /api/documents': { body: [] },
}

const guest = (name: string) => ({ ...booking().guest!, name })

/** Three bookings to review, in a different order than their arrival; decisions take them out of the queue. */
function queueApi() {
  const bookings = new Map<number, Booking>([
    [503, booking({ id: 503, arrival: '2026-12-10', departure: '2026-12-12', reviewState: 'NEW', guest: guest('Clara Gast') })],
    [501, booking({ id: 501, arrival: '2026-12-01', departure: '2026-12-03', reviewState: 'NEW', guest: guest('Anna Gast') })],
    [502, booking({
      id: 502, arrival: '2026-12-05', departure: '2026-12-07', reviewState: 'CHANGE', guest: guest('Bruno Gast'),
      proposal: { type: 'CHANGE', arrival: null, departure: '2026-12-08', adults: null, children: null, pets: null, expectedRent: null, guest: null },
    })],
  ])
  const decide = (id: number, changes: Partial<Booking>) => {
    const b = { ...bookings.get(id)!, ...changes, reviewState: 'NONE' as const, proposal: null, version: bookings.get(id)!.version + 1 }
    bookings.set(id, b)
    return { body: b }
  }
  const handlers: Record<string, unknown> = {
    ...base,
    'GET /api/vacationrental/bookings': () => ({ body: bookingPage([...bookings.values()].filter((b) => b.reviewState !== 'NONE')) }),
  }
  ;[501, 502, 503].forEach((id) => {
    handlers[`GET /api/vacationrental/bookings/${id}`] = () => ({ body: bookings.get(id) })
    handlers[`POST /api/vacationrental/bookings/${id}/confirm`] = () => decide(id, {})
  })
  handlers['POST /api/vacationrental/bookings/502/proposal/apply'] = () => decide(502, { departure: '2026-12-08' })
  mockApi(handlers as Parameters<typeof mockApi>[0])
}

const position = () => screen.getByRole('navigation', { name: 'Prüfen' })

describe('Review mode', () => {
  it('starts from the list and clicks through in order of arrival', async () => {
    queueApi()
    const { router } = renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Prüfen (3)' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/ferienvermietung/buchungen/501'))
    expect(router.state.location.search).toBe('?pruefen=1')
    expect(await screen.findByRole('heading', { name: 'Anna Gast' })).toBeInTheDocument()
    await waitFor(() => expect(position()).toHaveTextContent('1 von 3'))
    expect(within(position()).getByRole('button', { name: 'Zurück' })).toBeDisabled()

    await user.click(within(position()).getByRole('button', { name: 'Weiter' }))
    expect(await screen.findByRole('heading', { name: 'Bruno Gast' })).toBeInTheDocument()
    await waitFor(() => expect(position()).toHaveTextContent('2 von 3'))

    // The arrow keys move as well, as long as no field has the focus.
    fireEvent.keyDown(window, { key: 'ArrowRight' })
    expect(await screen.findByRole('heading', { name: 'Clara Gast' })).toBeInTheDocument()
    expect(within(position()).getByRole('button', { name: 'Weiter' })).toBeDisabled()
    fireEvent.keyDown(window, { key: 'ArrowLeft' })
    expect(await screen.findByRole('heading', { name: 'Bruno Gast' })).toBeInTheDocument()
  })

  it('moves on after each decision until all are done', async () => {
    queueApi()
    const { router } = renderApp('/ferienvermietung/pruefen')
    const user = userEvent.setup()
    expect(await screen.findByRole('heading', { name: 'Anna Gast' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Bestätigen' }))

    // The change shows old and new values and moves on only with its own decision.
    expect(await screen.findByRole('heading', { name: 'Bruno Gast' })).toBeInTheDocument()
    await waitFor(() => expect(position()).toHaveTextContent('1 von 2'))
    expect(screen.getByRole('table', { name: 'Vorgeschlagene Änderung' })).toHaveTextContent('08.12.2026')
    await user.click(screen.getByRole('button', { name: 'Übernehmen' }))

    expect(await screen.findByRole('heading', { name: 'Clara Gast' })).toBeInTheDocument()
    await waitFor(() => expect(position()).toHaveTextContent('1 von 1'))
    await user.click(screen.getByRole('button', { name: 'Bestätigen' }))

    expect(await screen.findByText('Alles geprüft.')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/ferienvermietung/pruefen')
    expect(screen.getByRole('link', { name: 'Zur Liste' })).toHaveAttribute('href', '/ferienvermietung/buchungen')
  })

  it('keeps the list filters in the queue', async () => {
    queueApi()
    const { router } = renderApp('/ferienvermietung/buchungen?unitId=70&sort=nights,asc')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Prüfen (3)' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/ferienvermietung/buchungen/501'))
    // Filters stay, sorting of the list does not: the queue is in order of arrival.
    expect(router.state.location.search).toBe('?unitId=70&pruefen=1')
    await user.click(await screen.findByRole('button', { name: 'Zur Liste' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/ferienvermietung/buchungen'))
    expect(router.state.location.search).toBe('?unitId=70')
  })

  it('does not move while typing and asks before dropping unsaved edits', async () => {
    queueApi()
    renderApp('/ferienvermietung/buchungen/501?pruefen=1')
    const user = userEvent.setup()
    const name = await screen.findByRole('textbox', { name: 'Name' })
    await waitFor(() => expect(position()).toHaveTextContent('1 von 3'))
    await user.clear(name)
    await user.type(name, 'Anna Neu')
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('heading', { name: 'Anna Gast' })).toBeInTheDocument()

    await user.click(within(position()).getByRole('button', { name: 'Weiter' }))
    const dialog = await screen.findByRole('dialog', { name: 'Ungespeicherte Änderungen verwerfen?' })
    await user.click(within(dialog).getByRole('button', { name: 'Abbrechen' }))
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('Anna Neu')

    await user.click(within(position()).getByRole('button', { name: 'Weiter' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Verwerfen' }))
    expect(await screen.findByRole('heading', { name: 'Bruno Gast' })).toBeInTheDocument()
  })

  it('says so when there is nothing to review', async () => {
    mockApi({ ...base, 'GET /api/vacationrental/bookings': { body: bookingPage([]) } })
    renderApp('/ferienvermietung/pruefen?unitId=70')
    expect(await screen.findByText('Alles geprüft.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zur Liste' })).toHaveAttribute('href', '/ferienvermietung/buchungen?unitId=70')
  })
})

describe('Queue order', () => {
  const at = (id: number, arrival: string) => booking({ id, arrival })
  const items = [at(3, '2026-12-10'), at(1, '2026-12-01'), at(2, '2026-12-01'), at(4, '2026-12-05')]

  it('finds the next and previous booking by arrival, then id', () => {
    expect(nextAfter(items, { id: 1, arrival: '2026-12-01' })?.id).toBe(2)
    expect(nextAfter(items, { id: 2, arrival: '2026-12-01' })?.id).toBe(4)
    expect(previousBefore(items, { id: 4, arrival: '2026-12-05' })?.id).toBe(2)
    expect(nextAfter(items, { id: 3, arrival: '2026-12-10' })).toBeUndefined()
  })

  it('finds the next one also after the current booking left the queue', () => {
    const left = items.filter((b) => b.id !== 4)
    expect(nextAfter(left, { id: 4, arrival: '2026-12-05' })?.id).toBe(3)
  })
})
