import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, normalUser, renderApp } from '@/test/renderApp'
import { account, categories, page, transaction } from '@/core/ledger/fixtures'
import type { Property } from './api'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': { body: [] },
  'GET /api/categories': { body: categories },
  'GET /api/accounts': { body: [account()] },
}

function property(overrides: Partial<Property> = {}): Property {
  return {
    id: 7,
    name: 'Ferienhaus Ostsee',
    street: 'Dünenweg 3',
    postalCode: '18225',
    city: 'Kühlungsborn',
    legalEntity: { id: 10, name: 'Anna privat', kind: 'PRIVATE_PERSON' },
    multiUnit: false,
    archived: false,
    myRole: 'OWNER',
    units: [{ id: 70, name: 'Ferienhaus Ostsee', usage: 'VACATION_RENTAL', archived: false, myRole: 'OWNER' }],
    ...overrides,
  }
}

const house = property({
  id: 8,
  name: 'Haus am Deich',
  multiUnit: true,
  units: [
    { id: 81, name: 'Whg. 1', usage: 'VACATION_RENTAL', archived: false, myRole: 'OWNER' },
    { id: 82, name: 'Whg. 2', usage: 'VACATION_RENTAL', archived: false, myRole: 'OWNER' },
  ],
})

describe('Navigation', () => {
  it('shows "Immobilien" to every signed-in user', async () => {
    mockApi({ ...base, 'GET /api/auth/me': authenticated(normalUser) })
    renderApp('/')
    expect(await screen.findByRole('link', { name: /Objekte/ })).toHaveAttribute('href', '/immobilien')
  })

  it('shows "Seite nicht gefunden" for an unknown property', async () => {
    mockApi({
      ...base,
      'GET /api/properties/99': { status: 404, body: { detail: 'Objekt nicht gefunden' } },
      'GET /api/transactions': { body: page([]) },
    })
    renderApp('/immobilien/99')
    expect(await screen.findByRole('heading', { name: 'Seite nicht gefunden' })).toBeInTheDocument()
  })
})

describe('Properties', () => {
  it('creates a single-unit property', async () => {
    let sent: unknown
    mockApi({
      ...base,
      'GET /api/properties': { body: [] },
      'GET /api/legal-entities': { body: [{ id: 10, name: 'Anna privat', kind: 'PRIVATE_PERSON', taxNumber: null, personal: true, accountCount: 0 }] },
      'POST /api/properties': (init) => {
        sent = JSON.parse(String(init?.body))
        return { status: 201, body: property() }
      },
      'GET /api/properties/7': { body: property() },
      'GET /api/transactions': { body: page([]) },
    })
    renderApp('/immobilien')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Objekt anlegen' }))
    await user.type(await screen.findByLabelText('Name'), 'Ferienhaus Ostsee')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() =>
      expect(sent).toMatchObject({ legalEntityId: 10, name: 'Ferienhaus Ostsee', multiUnit: false, firstUnit: { usage: 'VACATION_RENTAL' } }),
    )
    expect(await screen.findByRole('heading', { name: 'Ferienhaus Ostsee' })).toBeInTheDocument()
  })

  it('shows the message for a duplicate unit name', async () => {
    mockApi({
      ...base,
      'GET /api/properties/8': { body: house },
      'GET /api/transactions': { body: page([]) },
      'POST /api/properties/8/units': { status: 409, body: { detail: 'Eine Einheit mit diesem Namen existiert bereits' } },
    })
    renderApp('/immobilien/8')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Einheit hinzufügen' }))
    await user.type(await screen.findByLabelText('Name'), 'whg. 1')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Eine Einheit mit diesem Namen existiert bereits')).toBeInTheDocument()
  })

  it('shows a unit-only user the property frame with their unit', async () => {
    const frame = property({
      ...house,
      myRole: null,
      units: [{ id: 82, name: 'Whg. 2', usage: 'VACATION_RENTAL', archived: false, myRole: 'EDITOR' }],
    })
    mockApi({ ...base, 'GET /api/auth/me': authenticated(normalUser), 'GET /api/properties': { body: [frame] } })
    renderApp('/immobilien')
    const card = await screen.findByRole('region', { name: 'Haus am Deich' })
    expect(within(card).getByText('Whg. 2')).toBeInTheDocument()
    expect(within(card).queryByText('Whg. 1')).not.toBeInTheDocument()
    expect(within(card).getByText('Bearbeiten')).toBeInTheDocument()
  })
})

describe('Property detail', () => {
  it('shows a holiday home without a unit list', async () => {
    mockApi({ ...base, 'GET /api/properties/7': { body: property() }, 'GET /api/transactions': { body: page([]) } })
    renderApp('/immobilien/7')
    expect(await screen.findByRole('heading', { name: 'Ferienhaus Ostsee' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Einheiten' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Freigeben' })).toBeInTheDocument()
  })

  it('shows a viewer no buttons for editing', async () => {
    mockApi({
      ...base,
      'GET /api/properties/8': { body: { ...house, myRole: 'VIEWER', units: house.units.map((u) => ({ ...u, myRole: 'VIEWER' })) } },
      'GET /api/transactions': { body: page([]) },
    })
    renderApp('/immobilien/8')
    expect(await screen.findByRole('region', { name: 'Einheiten' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Einheit hinzufügen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Whg. 1 bearbeiten' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Freigaben' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Freigaben Whg. 1' })).toBeInTheDocument()
  })
})

describe('Unit shares', () => {
  const shareMocks = (canManage: boolean, myRole: 'OWNER' | 'EDITOR') => ({
    ...base,
    'GET /api/properties/8': {
      body: canManage ? house : { ...house, myRole: null, units: [{ ...house.units[1], myRole: 'EDITOR' }] },
    },
    'GET /api/transactions': { body: page([]) },
    'GET /api/shares/unit/82': {
      body: { myRole, canManage, shares: [{ userId: 3, username: 'carla', displayName: 'Carla', role: 'EDITOR' }] },
    },
  })

  it('offers only "Lesen" and "Bearbeiten" for units', async () => {
    mockApi(shareMocks(true, 'OWNER'))
    renderApp('/immobilien/8')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Whg. 2 freigeben' }))
    await user.click(await screen.findByRole('combobox', { name: 'Rolle für Einladung' }))
    const options = await screen.findAllByRole('option')
    expect(options.map((o) => o.textContent)).toEqual(['Lesen', 'Bearbeiten'])
  })

  it('shows a unit editor no actions', async () => {
    mockApi({ ...shareMocks(false, 'EDITOR'), 'GET /api/auth/me': authenticated(normalUser) })
    renderApp('/immobilien/8')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Freigaben Whg. 2' }))
    const list = await screen.findByRole('list', { name: 'Freigaben' })
    expect(within(list).getByText('Bearbeiten')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Einladen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Rolle von Carla' })).not.toBeInTheDocument()
  })
})

describe('Transactions with targets', () => {
  it('sends the unit chosen in the dialog', async () => {
    let sent: unknown
    mockApi({
      ...base,
      'GET /api/accounts/1': { body: account() },
      'GET /api/transactions': { body: page([]) },
      'GET /api/properties': { body: [property(), house] },
      'POST /api/transactions': (init) => {
        sent = JSON.parse(String(init?.body))
        return { status: 201, body: transaction() }
      },
    })
    renderApp('/finanzen/konten/1')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Transaktion erfassen' }))
    await user.type(await screen.findByLabelText('Betrag (€)'), '95')
    await user.click(screen.getByRole('combobox', { name: 'Objekt' }))
    await user.click(await screen.findByRole('option', { name: 'Whg. 2' }))
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(sent).toMatchObject({ unitId: 82, propertyId: null }))
  })

  it('sends the target filter as query parameter', async () => {
    const fetchMock = mockApi({
      ...base,
      'GET /api/transactions': { body: page([]) },
      'GET /api/properties': { body: [property(), house] },
    })
    renderApp('/finanzen/transaktionen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('combobox', { name: 'Objekt' }))
    await user.click(await screen.findByRole('option', { name: 'Haus am Deich (alle)' }))
    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([input]) => String(input).includes('propertyId=8'))).toBe(true),
    )
    await user.click(screen.getByRole('combobox', { name: 'Objekt' }))
    await user.click(await screen.findByRole('option', { name: 'Ferienhaus Ostsee' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([input]) => String(input).includes('unitId=70'))).toBe(true))
  })

  it('shows a reduced row with the target and without actions', async () => {
    mockApi({
      ...base,
      'GET /api/auth/me': authenticated(normalUser),
      'GET /api/accounts': { body: [] },
      'GET /api/properties': { body: [] },
      'GET /api/transactions': {
        body: page([
          transaction({ accountId: null, accountName: null, note: null, counterparty: 'Gast Müller', amount: '95.00', unitId: 82, targetName: 'Haus am Deich · Whg. 2', access: 'TARGET' }),
        ]),
      },
    })
    renderApp('/finanzen/transaktionen')
    const table = await screen.findByRole('table')
    expect(await within(table).findByText('Gast Müller')).toBeInTheDocument()
    expect(within(table).getAllByText(/Haus am Deich · Whg. 2/).length).toBeGreaterThan(0)
    expect(within(table).queryByRole('button', { name: 'Transaktion bearbeiten' })).not.toBeInTheDocument()
  })
})
