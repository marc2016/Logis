import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { FieldLocation, SourceText } from './api'
import { booking, waldeck } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [waldeck] },
  'GET /api/vacationrental/agencies': { body: [] },
  'GET /api/documents': { body: [] },
}

const SUBJECT = 'Neue Buchung für Haus Waldeck - Talstadt'
const TEXT = 'Buchungsnummer\t250905\nAnreise\t06.11.2026\nAbreise\t08.11.2026\nName\tErika Musterfrau'
const CHANGE = 'Ihre Buchung 250905 wurde geändert.\nAbreise\t09.11.2026'

function at(sourceId: number, text: string, value: string, sourceValue: string, differs = false, part: FieldLocation['part'] = 'TEXT'): FieldLocation {
  const start = text.indexOf(value)
  return { sourceId, part, start, end: start + value.length, sourceValue, differs }
}

function source(id: number, label: string, text: string): SourceText {
  return { id, label, sender: 'buchung@example.org', receivedAt: '2026-11-06T11:15:00Z', subject: SUBJECT, text }
}

/** An imported booking whose guest name was corrected by hand, with a proposed new departure from a second mail. */
const imported = booking({
  guest: { name: 'Erika Muster-Frau', street: null, postalCode: null, city: null, country: null, phone: null, email: null },
  agency: { id: 1, name: 'Fewo & Meer', archived: false },
  reviewState: 'CHANGE',
  proposal: { type: 'CHANGE', arrival: null, departure: '2026-11-09', adults: null, children: null, pets: null, expectedRent: null, guest: null },
  sources: [
    { id: 1, kind: 'MAIL', label: 'Fewo & Meer Buchungsbestätigung', receivedAt: '2026-11-06T11:15:00Z' },
    { id: 2, kind: 'MAIL', label: 'Fewo & Meer Änderung', receivedAt: '2026-11-07T09:00:00Z' },
  ],
  locations: {
    externalRef: at(1, TEXT, '250905', '250905'),
    propertyName: at(1, SUBJECT, 'Haus Waldeck - Talstadt', 'Haus Waldeck - Talstadt', false, 'SUBJECT'),
    arrival: at(1, TEXT, '06.11.2026', '2026-11-06'),
    departure: at(1, TEXT, '08.11.2026', '2026-11-08'),
    guestName: at(1, TEXT, 'Erika Musterfrau', 'Erika Musterfrau', true),
  },
  proposalLocations: { departure: at(2, CHANGE, '09.11.2026', '2026-11-09') },
})

function api(current = imported) {
  return {
    ...base,
    'GET /api/vacationrental/bookings/500': { body: current },
    'GET /api/vacationrental/bookings/500/sources/1': { body: source(1, 'Fewo & Meer Buchungsbestätigung', TEXT) },
    'GET /api/vacationrental/bookings/500/sources/2': { body: source(2, 'Fewo & Meer Änderung', CHANGE) },
  }
}

const activeMark = () => document.querySelector<HTMLElement>('mark[data-active="true"]')

describe('Booking page', () => {
  it('highlights the important details in its header', async () => {
    mockApi(api())
    renderApp('/ferienvermietung/buchungen/500')
    expect(await screen.findByRole('heading', { name: 'Erika Muster-Frau' })).toBeInTheDocument()
    const details = screen.getByLabelText('Wichtige Angaben')
    expect(details).toHaveTextContent('06.11.2026 – 08.11.2026 · 2 Nächte')
    expect(details).toHaveTextContent('Haus Waldeck')
    expect(details).toHaveTextContent('Fewo & Meer')
    expect(details).toHaveTextContent('Nr. 250905')
    expect(details).toHaveTextContent('248,58 €')
    expect(screen.getByText('zu prüfen')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zurück zu Buchungen' })).toHaveAttribute('href', '/ferienvermietung/buchungen')
  })

  it('shows where a field comes from and focuses a field from its passage', async () => {
    mockApi(api())
    renderApp('/ferienvermietung/buchungen/500')
    const user = userEvent.setup()
    const panel = await screen.findByRole('region', { name: 'Quelle' })
    // The newest source is shown first.
    expect(await within(panel).findByText('Ihre Buchung 250905 wurde geändert.', { exact: false })).toBeInTheDocument()

    await user.click(screen.getByLabelText('Anreise'))
    await waitFor(() => expect(activeMark()).toHaveTextContent('06.11.2026'))
    expect(activeMark()).toHaveAttribute('data-fields', 'arrival')
    expect(within(panel).getByRole('tab', { name: /Buchungsbestätigung/ })).toHaveAttribute('aria-selected', 'true')
    expect(within(panel).getByLabelText('Betreff').querySelector('mark')).toHaveTextContent('Haus Waldeck - Talstadt')

    await user.click(within(panel).getByText('Erika Musterfrau'))
    expect(screen.getByLabelText('Name')).toHaveFocus()
    expect(activeMark()).toHaveTextContent('Erika Musterfrau')
  })

  it('marks a value edited by hand with the value from the mail', async () => {
    mockApi(api())
    renderApp('/ferienvermietung/buchungen/500')
    const name = (await screen.findByLabelText('Name')).closest('[data-field]') as HTMLElement
    expect(within(name).getByText('weicht vom Dokument ab · laut Dokument: Erika Musterfrau')).toBeInTheDocument()
    const arrival = screen.getByLabelText('Anreise').closest('[data-field]') as HTMLElement
    expect(within(arrival).queryByText(/weicht vom Dokument ab/)).not.toBeInTheDocument()
  })

  it('shows a proposed value in the mail that proposes it', async () => {
    mockApi(api())
    renderApp('/ferienvermietung/buchungen/500')
    const user = userEvent.setup()
    const diff = await screen.findByRole('table', { name: 'Vorgeschlagene Änderung' })
    await user.click(screen.getByLabelText('Anreise'))
    await user.click(within(diff).getByRole('button', { name: '09.11.2026' }))
    await waitFor(() => expect(activeMark()).toHaveTextContent('09.11.2026'))
    expect(screen.getByRole('tab', { name: /Änderung/ })).toHaveAttribute('aria-selected', 'true')
  })

  it('says so for a booking that does not exist', async () => {
    mockApi({ ...base, 'GET /api/vacationrental/bookings/404': { status: 404, body: { detail: 'Buchung nicht gefunden' } } })
    renderApp('/ferienvermietung/buchungen/404')
    expect(await screen.findByRole('alert')).toHaveTextContent('Buchung nicht gefunden')
  })

  it('returns to the filtered list it was opened from', async () => {
    mockApi({
      ...api(booking()),
      'GET /api/vacationrental/bookings': { body: { items: [booking()], page: 0, size: 50, totalItems: 1 } },
    })
    const { router } = renderApp('/ferienvermietung/buchungen?propertyId=7')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Buchung 250905/ }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/ferienvermietung/buchungen/500'))
    expect(await screen.findByRole('link', { name: 'Zurück zu Buchungen' })).toHaveAttribute('href', '/ferienvermietung/buchungen?propertyId=7')
    // A booking recorded by hand has no source.
    expect(screen.getByText('Keine Quelle – die Buchung wurde von Hand erfasst.')).toBeInTheDocument()
  })
})
