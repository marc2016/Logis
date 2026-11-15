import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { Booking } from './api'
import { booking, bookingPage, seeufer, statement } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [seeufer] },
  'GET /api/vacationrental/agencies': { body: [] },
}

function bookings(n: number, overrides: (i: number) => Partial<Booking> = () => ({})): Booking[] {
  return Array.from({ length: n }, (_, i) => booking({ id: 500 + i, externalRef: `25090${i}`, ...overrides(i) }))
}

const toolbar = () => screen.getByRole('toolbar', { name: 'Aktionen für die Auswahl' })

describe('Bulk actions on bookings', () => {
  it('confirms all bookings to review, and reports bookings that are not', async () => {
    let list = bookings(3, (i) => ({ reviewState: i < 2 ? 'NEW' : 'NONE' }))
    const confirmed: number[] = []
    const handlers: Record<string, unknown> = {}
    list.forEach((b) => {
      handlers[`POST /api/vacationrental/bookings/${b.id}/confirm`] = () => {
        confirmed.push(b.id)
        list = list.map((x) => (x.id === b.id ? { ...x, reviewState: 'NONE' } : x))
        return { body: { ...b, reviewState: 'NONE' } }
      }
    })
    mockApi({ ...base, ...(handlers as object), 'GET /api/vacationrental/bookings': () => ({ body: bookingPage(list) }) })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
    expect(within(toolbar()).getByText('3 ausgewählt')).toBeInTheDocument()
    await user.click(within(toolbar()).getByRole('button', { name: 'Bestätigen' }))
    expect(await screen.findByText('2 bestätigt, 1 nicht: Die Buchung ist nicht „zu prüfen“')).toBeInTheDocument()
    expect(confirmed.sort()).toEqual([500, 501])
    // The booking that failed stays selected.
    expect(within(toolbar()).getByText('1 ausgewählt')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /Buchung 250902 .* auswählen/ })).toBeChecked()
  })

  it('deletes after confirmation; a settled booking fails with the server message', async () => {
    let list = bookings(3)
    const handlers: Record<string, unknown> = {}
    list.forEach((b, i) => {
      handlers[`DELETE /api/vacationrental/bookings/${b.id}`] = () => {
        if (i === 1) return { status: 409, body: { detail: 'Die Buchung ist abgerechnet und kann nur storniert werden' } }
        list = list.filter((x) => x.id !== b.id)
        return { status: 204 }
      }
    })
    mockApi({ ...base, ...(handlers as object), 'GET /api/vacationrental/bookings': () => ({ body: bookingPage(list) }) })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
    await user.click(within(toolbar()).getByRole('button', { name: 'Löschen' }))
    expect(within(toolbar()).getByText('3 Einträge löschen?')).toBeInTheDocument()
    await user.click(within(toolbar()).getByRole('button', { name: 'Ja' }))
    expect(await screen.findByText('2 gelöscht, 1 nicht: Die Buchung ist abgerechnet und kann nur storniert werden')).toBeInTheDocument()
    await waitFor(() => expect(screen.getAllByRole('row', { name: /^Buchung/ })).toHaveLength(1))
  })

  it('assigns the selection to a statement with commission and cleaning', async () => {
    const list = bookings(3, (i) => ({ expectedRent: i === 0 ? '248.58' : '400.00' }))
    const sent: Record<string, unknown> = {}
    const handlers: Record<string, unknown> = {}
    list.forEach((b) => {
      handlers[`PUT /api/vacationrental/statements/900/lines/${b.id}`] = (init?: RequestInit) => {
        if (b.id === 502) return { status: 400, body: { detail: 'Die Buchung gehört zu einer anderen Einheit' } }
        sent[b.id] = JSON.parse(String(init?.body))
        return { body: statement() }
      }
    })
    mockApi({
      ...base,
      ...(handlers as object),
      'GET /api/vacationrental/bookings': { body: bookingPage(list) },
      'GET /api/vacationrental/statements': { body: [statement()] },
    })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
    await user.click(within(toolbar()).getByRole('button', { name: 'Abrechnung zuordnen' }))
    const dialog = await screen.findByRole('dialog', { name: 'Abrechnung zuordnen' })
    await user.click(within(dialog).getByRole('combobox', { name: 'Abrechnung' }))
    await user.click(await screen.findByRole('option', { name: /Ferienwohnungen Nord/ }))
    await user.type(within(dialog).getByLabelText('Provision (%)'), '15')
    await user.type(within(dialog).getByLabelText('Reinigung je Buchung (€)'), '60,00')
    await user.click(within(dialog).getByRole('button', { name: 'Zuordnen' }))
    expect(await screen.findByText('2 zugeordnet, 1 nicht: Die Buchung gehört zu einer anderen Einheit')).toBeInTheDocument()
    expect(sent).toEqual({
      500: { gross: '248.58', commission: '37.29', cleaning: '60.00' },
      501: { gross: '400.00', commission: '60.00', cleaning: '60.00' },
    })
  })

  it('shows viewers no checkboxes', async () => {
    mockApi({
      ...base,
      'GET /api/vacationrental/units': { body: [{ ...seeufer, myRole: 'VIEWER' }] },
      'GET /api/vacationrental/bookings': { body: bookingPage(bookings(2, () => ({ canEdit: false }))) },
    })
    renderApp('/ferienvermietung/buchungen')
    expect(await screen.findAllByRole('row', { name: /^Buchung/ })).toHaveLength(2)
    expect(screen.queryByRole('checkbox', { name: /auswählen/ })).not.toBeInTheDocument()
  })
})
