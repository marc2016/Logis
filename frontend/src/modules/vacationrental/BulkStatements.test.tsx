import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { Statement } from './api'
import { booking, bookingPage, seeufer, statement } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [seeufer] },
}

const toolbar = () => screen.getByRole('toolbar', { name: 'Aktionen für die Auswahl' })

describe('Bulk actions on statements', () => {
  it('deletes selected statements from the list', async () => {
    let list = [statement({ id: 900 }), statement({ id: 901, statementDate: '2026-12-05' })]
    mockApi({
      ...base,
      'GET /api/vacationrental/statements': () => ({ body: list }),
      'DELETE /api/vacationrental/statements/900': () => ((list = list.filter((s) => s.id !== 900)), { status: 204 }),
      'DELETE /api/vacationrental/statements/901': () => ((list = list.filter((s) => s.id !== 901)), { status: 204 }),
    })
    renderApp('/ferienvermietung/abrechnungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
    await user.click(within(toolbar()).getByRole('button', { name: 'Löschen' }))
    await user.click(within(toolbar()).getByRole('button', { name: 'Ja' }))
    expect(await screen.findByText('2 gelöscht')).toBeInTheDocument()
    expect(await screen.findByText('Noch keine Abrechnungen.')).toBeInTheDocument()
  })

  it('fills a statement with the preselected bookings of its period and removes lines again', async () => {
    const october = statement({ id: 900, periodFrom: '2026-10-01', periodTo: '2026-10-31' })
    const inPeriod = [
      booking({ id: 601, arrival: '2026-10-02', departure: '2026-10-09', expectedRent: '420.00' }),
      booking({ id: 602, arrival: '2026-10-16', departure: '2026-10-20', expectedRent: '380.00' }),
    ]
    const later = booking({ id: 603, arrival: '2026-11-10', departure: '2026-11-12', expectedRent: '200.00' })
    let current: Statement = october
    const sent: Record<number, unknown> = {}
    const put = (b: typeof later) => (init?: RequestInit) => {
      sent[b.id] = JSON.parse(String(init?.body))
      const line = { booking: { ...b, status: 'SETTLED' as const }, gross: b.expectedRent!, commission: '0.00', cleaning: '0.00', net: b.expectedRent! }
      current = { ...current, lines: [...current.lines, line], lineCount: current.lines.length + 1 }
      return { body: current }
    }
    mockApi({
      ...base,
      'GET /api/vacationrental/statements/900': () => ({ body: current }),
      'GET /api/vacationrental/bookings': { body: bookingPage([...inPeriod, later]) },
      'PUT /api/vacationrental/statements/900/lines/601': put(inPeriod[0]),
      'PUT /api/vacationrental/statements/900/lines/602': put(inPeriod[1]),
      'DELETE /api/vacationrental/statements/900/lines/601': () => {
        current = { ...current, lines: current.lines.filter((l) => l.booking.id !== 601) }
        return { body: current }
      },
    })
    renderApp('/ferienvermietung/abrechnungen/900')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Buchungen zuordnen' }))
    const dialog = await screen.findByRole('dialog', { name: 'Buchungen zuordnen' })
    const list = await within(dialog).findByRole('list', { name: 'Offene Buchungen' })
    const boxes = within(list).getAllByRole('checkbox')
    expect(boxes.map((b) => b.getAttribute('data-state'))).toEqual(['checked', 'checked', 'unchecked'])
    await user.type(within(dialog).getByLabelText('Provision (%)'), '15')
    await user.type(within(dialog).getByLabelText('Reinigung je Buchung (€)'), '60')
    await user.click(within(dialog).getByRole('button', { name: '2 zuordnen' }))
    expect(await screen.findByText('2 zugeordnet')).toBeInTheDocument()
    expect(sent).toEqual({
      601: { gross: '420.00', commission: '63.00', cleaning: '60.00' },
      602: { gross: '380.00', commission: '57.00', cleaning: '60.00' },
    })

    await user.click(await screen.findByRole('checkbox', { name: /Position 02\.10\.2026/ }))
    await user.click(within(toolbar()).getByRole('button', { name: 'Entfernen' }))
    expect(await screen.findByText('1 entfernt')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: /Position 02\.10\.2026/ })).not.toBeInTheDocument()
  })
})
