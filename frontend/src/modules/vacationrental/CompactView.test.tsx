import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { Booking, FieldLocation } from './api'
import { booking, bookingPage, waldeck } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [waldeck] },
  'GET /api/vacationrental/agencies': { body: [] },
  'GET /api/documents': { body: [] },
}

const KEY = 'vacationrental.bookingView'

/** The booking API plus the settings store; returns what was saved. */
function api(view: string | null, bookings: Booking[] = [booking()]) {
  const preferences: Record<string, unknown> = view ? { [KEY]: view } : {}
  const sent: { settings: unknown[]; saved: unknown[] } = { settings: [], saved: [] }
  const handlers: Record<string, unknown> = {
    ...base,
    'GET /api/preferences': () => ({ body: { ...preferences } }),
    [`PUT /api/preferences/${KEY}`]: (init: RequestInit | undefined) => {
      preferences[KEY] = JSON.parse(String(init?.body))
      sent.settings.push(preferences[KEY])
      return { status: 204 }
    },
    'GET /api/vacationrental/bookings': { body: bookingPage(bookings) },
  }
  bookings.forEach((b) => {
    handlers[`GET /api/vacationrental/bookings/${b.id}`] = { body: b }
    handlers[`PUT /api/vacationrental/bookings/${b.id}`] = (init: RequestInit | undefined) => {
      sent.saved.push(JSON.parse(String(init?.body)))
      return { body: { ...b, version: b.version + 1 } }
    }
  })
  mockApi(handlers as Parameters<typeof mockApi>[0])
  return sent
}

const field = (name: string) => screen.queryByLabelText(name, { selector: 'input, button' })
const COMPACT = ['Anreise', 'Abreise', 'Erwachsene', 'Kinder', 'Mietpreis (€)']
const HIDDEN = ['Buchungsnummer', 'Haustiere', 'Name', 'Straße', 'E-Mail']

describe('Compact view', () => {
  it('shows all fields by default and only the main ones in „Kompakt“, remembered for the user', async () => {
    const sent = api(null)
    renderApp('/ferienvermietung/buchungen/500')
    const user = userEvent.setup()
    expect(await screen.findByRole('textbox', { name: 'Name' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Alle Angaben' })).toHaveAttribute('aria-checked', 'true')

    await user.click(screen.getByRole('radio', { name: 'Kompakt' }))
    COMPACT.forEach((name) => expect(field(name), name).toBeInTheDocument())
    HIDDEN.forEach((name) => expect(field(name), name).not.toBeInTheDocument())
    expect(screen.queryByRole('region', { name: 'Dokumente' })).not.toBeInTheDocument()
    // The header keeps the details the form no longer shows.
    expect(screen.getByLabelText('Wichtige Angaben')).toHaveTextContent('Nr. 250905')
    await waitFor(() => expect(sent.settings).toEqual(['compact']))
  })

  it('opens in the remembered view and keeps the hidden values when saving', async () => {
    const sent = api('compact')
    renderApp('/ferienvermietung/buchungen/500')
    const user = userEvent.setup()
    const rent = await screen.findByRole('textbox', { name: 'Mietpreis (€)' })
    expect(field('Name')).not.toBeInTheDocument()
    await user.clear(rent)
    await user.type(rent, '300,00')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(sent.saved).toHaveLength(1))
    const body = sent.saved[0] as { expectedRent: string; guest: { name: string }; externalRef: string; pets: number }
    expect(body.expectedRent).toBe('300.00')
    expect(body.guest.name).toBe('Max Gast')
    expect(body.externalRef).toBe('250905')
  })

  it('shows a field that differs from its document also in „Kompakt“', async () => {
    const start = 'Name\tErika Musterfrau'.indexOf('Erika')
    const differs: FieldLocation = { sourceId: 1, part: 'TEXT', start, end: start + 16, sourceValue: 'Erika Musterfrau', differs: true }
    api('compact', [booking({ locations: { guestName: differs } })])
    renderApp('/ferienvermietung/buchungen/500')
    expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue('Max Gast')
    expect(screen.getByText(/weicht vom Dokument ab/)).toBeInTheDocument()
    expect(field('Straße')).not.toBeInTheDocument()
  })

  it('shows only the dates of an owner use', async () => {
    api('compact', [booking({ kind: 'OWNER', guest: null, expectedRent: null })])
    renderApp('/ferienvermietung/buchungen/500')
    expect(await screen.findByRole('textbox', { name: 'Anreise' })).toBeInTheDocument()
    expect(field('Abreise')).toBeInTheDocument()
    ;['Erwachsene', 'Kinder', 'Mietpreis (€)', 'Name'].forEach((name) => expect(field(name), name).not.toBeInTheDocument())
  })

  it('keeps the view in the review mode', async () => {
    api('compact', [booking({ reviewState: 'NEW' })])
    renderApp('/ferienvermietung/buchungen/500?pruefen=1')
    expect(await screen.findByRole('navigation', { name: 'Prüfen' })).toBeInTheDocument()
    expect(await screen.findByRole('textbox', { name: 'Mietpreis (€)' })).toBeInTheDocument()
    expect(field('Name')).not.toBeInTheDocument()
  })

  it('records a new booking with all fields', async () => {
    api('compact')
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Buchung' }))
    const dialog = await screen.findByRole('dialog', { name: 'Buchung erfassen' })
    expect(within(dialog).getByRole('textbox', { name: 'Name' })).toBeInTheDocument()
    expect(within(dialog).getByRole('textbox', { name: 'Buchungsnummer' })).toBeInTheDocument()
    expect(within(dialog).queryByRole('radio', { name: 'Kompakt' })).not.toBeInTheDocument()
  })
})
