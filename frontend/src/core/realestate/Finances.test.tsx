import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp } from '@/test/renderApp'
import { account, categories, page, transaction } from '@/core/ledger/fixtures'
import type { Finances, Property } from './api'

const months = Array.from({ length: 12 }, (_, i) => ({ month: i + 1, income: '0.00', expenses: '0.00', result: '0.00' }))
months[10] = { month: 11, income: '248.58', expenses: '417.29', result: '-168.71' }

const finances: Finances = {
  year: 2026,
  months,
  total: { month: 0, income: '248.58', expenses: '417.29', result: '-168.71' },
  items: [
    { date: '2026-11-30', unitId: 70, unitName: 'Haus Seeufer', kind: 'INCOME', label: 'Buchung 250905', amount: '248.58', sourceType: 'vacationrental.statement', sourceId: 900, categoryName: null, counterparty: null, editable: false },
    { date: '2026-11-12', unitId: null, unitName: null, kind: 'EXPENSE', label: 'Handwerker Meyer', amount: '320.00', sourceType: 'realestate.cost', sourceId: 5, categoryName: 'Wohnen', counterparty: 'Handwerker Meyer', editable: true },
  ],
  canRecord: true,
}

function property(overrides: Partial<Property> = {}): Property {
  return {
    id: 7,
    name: 'Haus Seeufer',
    street: null,
    postalCode: null,
    city: 'Nordhorn',
    legalEntity: { id: 10, name: 'Beispiel GbR', kind: 'PARTNERSHIP' as Property['legalEntity']['kind'] },
    multiUnit: false,
    archived: false,
    myRole: 'OWNER',
    units: [{ id: 70, name: 'Haus Seeufer', usage: 'VACATION_RENTAL', archived: false, myRole: 'OWNER' }],
    ...overrides,
  }
}

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': { body: [] },
  'GET /api/categories': { body: categories },
  'GET /api/accounts': { body: [account()] },
  'GET /api/transactions': { body: page([transaction({ propertyId: 7, targetName: 'Haus Seeufer' })]) },
  'GET /api/properties/7/finances': { body: finances },
}

describe('Property finances', () => {
  it('shows monthly totals, read-only contributed items and marks attributed transactions', async () => {
    mockApi({ ...base, 'GET /api/properties/7': { body: property() } })
    renderApp('/immobilien/7')
    const months = await screen.findByRole('table', { name: 'Monatsübersicht' })
    const november = within(months).getByRole('row', { name: /Nov/ })
    expect(within(november).getByText('248,58 €')).toBeInTheDocument()
    expect(within(november).getByText('417,29 €')).toBeInTheDocument()
    expect(within(november).getByText('-168,71 €')).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Buchung 250905/ })).toBeInTheDocument()
    expect(screen.getByText('nicht im Ergebnis')).toBeInTheDocument()
    // Single-unit property: no unit column or unit filter.
    expect(screen.queryByRole('columnheader', { name: /Einheit/ })).not.toBeInTheDocument()
  })

  it('records a cost with the unit field only for multi-unit properties', async () => {
    let sent: Record<string, unknown> | undefined
    mockApi({
      ...base,
      'GET /api/properties/7': { body: property() },
      'POST /api/properties/7/costs': (init) => {
        sent = JSON.parse(String(init?.body))
        return { status: 201, body: { id: 6, propertyId: 7, unitId: null, date: '2026-11-12', amount: '320.00', categoryId: null, counterparty: null, note: null, version: 0 } }
      },
    })
    renderApp('/immobilien/7')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Kosten' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByLabelText('Einheit')).not.toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('Betrag (€)'), '320,00')
    await user.type(within(dialog).getByLabelText('Empfänger'), 'Handwerker Meyer')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(sent).toMatchObject({ unitId: null, amount: '320.00', counterparty: 'Handwerker Meyer' }))
  })

  it('offers the unit for multi-unit properties', async () => {
    mockApi({
      ...base,
      'GET /api/properties/7': {
        body: property({
          multiUnit: true,
          units: [
            { id: 81, name: 'Whg. 1', usage: 'VACATION_RENTAL', archived: false, myRole: 'OWNER' },
            { id: 82, name: 'Whg. 2', usage: 'VACATION_RENTAL', archived: false, myRole: 'OWNER' },
          ],
        }),
      },
    })
    renderApp('/immobilien/7')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Kosten' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Ganzes Objekt')
  })
})
