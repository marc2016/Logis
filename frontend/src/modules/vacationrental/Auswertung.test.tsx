import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import type { MonthFigures, Overview, UnitTotal } from './api'
import { waldeck } from './fixtures'
import { ownerRate, unitsTotal } from './occupancyFigures'

function months(overrides: Record<number, Partial<MonthFigures>> = {}): MonthFigures[] {
  return Array.from({ length: 12 }, (_, i) => ({
    month: i + 1,
    nights: 0,
    occupancy: 0,
    ownerNights: 0,
    settledNet: '0.00',
    plannedRent: '0.00',
    ...overrides[i + 1],
  }))
}

function overview(year: number, overrides: Record<number, Partial<MonthFigures>> = {}): Overview {
  return {
    unitId: 70,
    year,
    months: months(overrides),
    total: { month: 0, nights: 11, occupancy: 3, ownerNights: 5, settledNet: '540.00', plannedRent: '248.58' },
  }
}

const flat = { ...waldeck, id: 71, name: 'Whg. 1', propertyId: 8, propertyName: 'Strandhaus', propertyMultiUnit: true }

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/vacationrental/units': { body: [waldeck, flat] },
}

function total(t: Partial<MonthFigures>): MonthFigures {
  return { month: 0, nights: 0, occupancy: 0, ownerNights: 0, settledNet: '0.00', plannedRent: '0.00', ...t }
}

describe('Evaluation charts', () => {
  it('shows „Belegung“ and „Einnahmen“ above the table of a unit', async () => {
    mockApi({
      ...base,
      'GET /api/vacationrental/occupancy/overview?unitId=70&year=2026': {
        body: overview(2026, { 11: { nights: 11, occupancy: 37, settledNet: '540.00', plannedRent: '248.58' } }),
      },
    })
    renderApp('/ferienvermietung/auswertung?unitId=70&year=2026')

    // jsdom has no layout, so Recharts draws no legend here; the e2e test checks the drawn charts.
    expect(await screen.findByRole('figure', { name: 'Belegung je Monat 2026' })).toBeInTheDocument()
    expect(screen.getByRole('figure', { name: 'Einnahmen je Monat 2026' })).toBeInTheDocument()
    expect(screen.getByText(/Beide werden nicht addiert/)).toBeInTheDocument()
    // The exact figures stay in the table.
    expect(within(screen.getByRole('row', { name: /November/ })).getByText('540,00 €')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: /2025/ })).not.toBeInTheDocument()
  })

  it('adds the previous year with the switch, kept in the address', async () => {
    const fetch = mockApi({
      ...base,
      'GET /api/vacationrental/occupancy/overview?unitId=70&year=2026': { body: overview(2026) },
      'GET /api/vacationrental/occupancy/overview?unitId=70&year=2025': {
        body: overview(2025, { 11: { occupancy: 20, settledNet: '300.00' } }),
      },
    })
    const { router } = renderApp('/ferienvermietung/auswertung?unitId=70&year=2026')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('checkbox', { name: 'Vorjahr einblenden' }))
    expect(router.state.location.search).toContain('previous=1')
    expect(await screen.findByRole('figure', { name: 'Belegung je Monat 2026 mit Vorjahr 2025' })).toBeInTheDocument()
    expect(screen.getByRole('figure', { name: 'Einnahmen je Monat 2026 mit Vorjahr 2025' })).toBeInTheDocument()
    // The previous year's values also stand in the table (the line colour is below 3:1 on white).
    const november = screen.getByRole('row', { name: /November/ })
    expect(within(november).getByText('20 %')).toBeInTheDocument()
    expect(within(november).getByText('300,00 €')).toBeInTheDocument()
    expect(fetch.mock.calls.some(([url]) => String(url).endsWith('year=2025'))).toBe(true)
  })

  it('keeps the switch after a reload', async () => {
    mockApi({
      ...base,
      'GET /api/vacationrental/occupancy/overview?unitId=70&year=2026': { body: overview(2026) },
      'GET /api/vacationrental/occupancy/overview?unitId=70&year=2025': { body: overview(2025) },
    })
    renderApp('/ferienvermietung/auswertung?unitId=70&year=2026&previous=1')
    expect(await screen.findByRole('checkbox', { name: 'Vorjahr einblenden' })).toBeChecked()
    expect(await screen.findByRole('columnheader', { name: 'Belegung 2025' })).toBeInTheDocument()
  })

  it('compares all units with a total row', async () => {
    const rows: UnitTotal[] = [
      { unitId: 70, unitName: 'Haus Waldeck', propertyId: 7, propertyName: 'Haus Waldeck', propertyMultiUnit: false, archived: false,
        total: total({ nights: 80, occupancy: 22, settledNet: '6400.00', plannedRent: '500.00' }) },
      { unitId: 71, unitName: 'Whg. 1', propertyId: 8, propertyName: 'Strandhaus', propertyMultiUnit: true, archived: true,
        total: total({ nights: 60, occupancy: 16, ownerNights: 7, settledNet: '4800.50' }) },
    ]
    const fetch = mockApi({
      ...base,
      'GET /api/vacationrental/occupancy/overview?unitId=70&year=2026': { body: overview(2026) },
      'GET /api/vacationrental/occupancy/overview/units?year=2026': { body: rows },
    })
    const { router } = renderApp('/ferienvermietung/auswertung?unitId=70&year=2026')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('combobox', { name: 'Einheit' }))
    await user.click(await screen.findByRole('option', { name: 'Alle Einheiten' }))
    expect(router.state.location.search).toContain('unitId=all')
    expect(await screen.findByRole('figure', { name: 'Belegung je Einheit 2026' })).toBeInTheDocument()
    expect(screen.getByRole('figure', { name: 'Einnahmen je Einheit 2026' })).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: 'Vorjahr einblenden' })).not.toBeInTheDocument()

    const table = screen.getByRole('table', { name: 'Alle Einheiten 2026' })
    expect(within(table).getByRole('row', { name: /Haus Waldeck/ })).toHaveTextContent('6.400,00 €')
    expect(within(table).getByRole('row', { name: /Strandhaus · Whg\. 1/ })).toHaveTextContent('archiviert')
    // 140 nights / (365 × 2) = 19 %
    const sum = within(table).getByRole('row', { name: /Summe/ })
    expect(sum).toHaveTextContent('140')
    expect(sum).toHaveTextContent('19 %')
    expect(sum).toHaveTextContent('11.200,50 €')
    expect(fetch.mock.calls.some(([url]) => String(url).includes('/overview/units?year=2026'))).toBe(true)

    // Back to one unit.
    await user.click(screen.getByRole('combobox', { name: 'Einheit' }))
    await user.click(await screen.findByRole('option', { name: 'Strandhaus · Whg. 1' }))
    await waitFor(() => expect(router.state.location.search).toContain('unitId=71'))
  })

  it('says so when no unit has figures', async () => {
    mockApi({ ...base, 'GET /api/vacationrental/occupancy/overview/units?year=2026': { body: [] } })
    renderApp('/ferienvermietung/auswertung?unitId=all&year=2026')
    expect(await screen.findByText('Keine Einheiten mit Zahlen in 2026.')).toBeInTheDocument()
  })
})

describe('Evaluation figures', () => {
  it('computes the owner-use share of a month', () => {
    expect(ownerRate(total({ month: 6, ownerNights: 5 }), 2027)).toBe(17)
    expect(ownerRate(total({ month: 2, ownerNights: 7 }), 2028)).toBe(24) // 29 days in a leap year
  })

  it('rounds the total occupancy half up over days × units', () => {
    const row = (nights: number): UnitTotal => ({
      unitId: nights, unitName: 'x', propertyId: 1, propertyName: 'x', propertyMultiUnit: false, archived: false, total: total({ nights }),
    })
    expect(unitsTotal([row(73), row(0)], 2026).occupancy).toBe(10) // 7300 / 730 = 10.0
    expect(unitsTotal([row(366)], 2028).occupancy).toBe(100)
    expect(unitsTotal([], 2026).occupancy).toBe(0)
  })
})
