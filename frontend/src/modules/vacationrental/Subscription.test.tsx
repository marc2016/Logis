import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { CalendarLink } from './api'
import { calendar, seeufer } from './fixtures'

const TOKEN = 'A'.repeat(43)

function setup() {
  let links: CalendarLink[] = []
  const created: unknown[] = []
  mockApi({
    'GET /api/auth/me': authenticated(),
    'GET /api/modules': vacationRentalActive,
    'GET /api/categories': { body: categories },
    'GET /api/vacationrental/units': { body: [seeufer] },
    'GET /api/vacationrental/occupancy/calendar?propertyId=7&month=2026-11': { body: calendar({ month: '2026-11' }) },
    'GET /api/vacationrental/occupancy/calendar?propertyId=7&month=2026-12': { body: calendar({ month: '2026-12' }) },
    'GET /api/vacationrental/occupancy/calendar?propertyId=7&month=2027-01': { body: calendar({ month: '2027-01' }) },
    'GET /api/vacationrental/calendar-links?unitId=70': () => ({ body: links }),
    'POST /api/vacationrental/calendar-links': (init) => {
      created.push(JSON.parse(String(init?.body)))
      const link = { id: 1, unitId: 70, name: 'Reinigung', withGuests: false, createdAt: '2026-11-07T10:00:00Z' }
      links = [link]
      return { status: 201, body: { link, path: `/api/ical/${TOKEN}.ics` } }
    },
    'DELETE /api/vacationrental/calendar-links/1': () => {
      links = []
      return { status: 204 }
    },
  })
  return { created }
}

describe('Calendar subscription', () => {
  it('creates a link without guest data, shows it once and revokes it', async () => {
    const { created } = setup()
    renderApp('/ferienvermietung/kalender?month=2026-11')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Abonnieren' }))
    const dialog = await screen.findByRole('dialog')
    expect(await within(dialog).findByText('Noch keine Links.')).toBeInTheDocument()

    await user.type(within(dialog).getByLabelText('Name'), 'Reinigung')
    await user.click(within(dialog).getByRole('button', { name: 'Link erstellen' }))
    expect(await within(dialog).findByLabelText('Neuer Link')).toHaveTextContent(`/api/ical/${TOKEN}.ics`)
    expect(created).toEqual([{ unitId: 70, name: 'Reinigung', withGuests: false }])
    const list = await within(dialog).findByRole('list', { name: 'Meine Links' })
    expect(within(list).getByText(/ohne Gastdaten/)).toBeInTheDocument()

    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    await user.click(within(dialog).getByRole('button', { name: 'Kopieren' }))
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining(`/api/ical/${TOKEN}.ics`))

    await user.click(within(dialog).getByRole('button', { name: 'Reinigung widerrufen' }))
    await waitFor(() => expect(within(dialog).getByText('Noch keine Links.')).toBeInTheDocument())
  })

  it('sends the guest data choice', async () => {
    const { created } = setup()
    renderApp('/ferienvermietung/kalender?month=2026-11')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Abonnieren' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('Name'), 'Ich')
    await user.click(within(dialog).getByRole('checkbox', { name: 'Mit Gastnamen und Buchungsnummer' }))
    await user.click(within(dialog).getByRole('button', { name: 'Link erstellen' }))
    await waitFor(() => expect(created).toEqual([{ unitId: 70, name: 'Ich', withGuests: true }]))
  })
})
