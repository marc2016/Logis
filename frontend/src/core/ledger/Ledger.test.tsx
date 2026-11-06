import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { adminUser, authenticated, mockApi, normalUser, renderApp } from '@/test/renderApp'
import { account, categories, page, transaction } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': { body: [] },
  'GET /api/categories': { body: categories },
}

describe('Navigation', () => {
  it('shows "Finanzen" to everyone and "Kategorien" only to admins', async () => {
    mockApi({ ...base, 'GET /api/auth/me': authenticated(normalUser) })
    renderApp('/')
    expect(await screen.findByRole('link', { name: /Konten/ })).toHaveAttribute('href', '/finanzen/konten')
    expect(screen.getByRole('link', { name: /Transaktionen/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Eigentümer/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Kategorien/ })).not.toBeInTheDocument()
  })

  it('shows "Kategorien" to admins', async () => {
    mockApi(base)
    renderApp('/')
    expect(await screen.findByRole('link', { name: /Kategorien/ })).toHaveAttribute('href', '/einstellungen/kategorien')
  })
})

describe('Owners', () => {
  it('creates an owner and shows the message for a duplicate name', async () => {
    let calls = 0
    mockApi({
      ...base,
      'GET /api/legal-entities': { body: [{ id: 10, name: 'Anna privat', kind: 'PRIVATE_PERSON', taxNumber: null, personal: true, accountCount: 1 }] },
      'POST /api/legal-entities': () =>
        ++calls === 1
          ? { status: 201, body: { id: 11, name: 'GbR Ostsee', kind: 'GBR', taxNumber: null, personal: false, accountCount: 0 } }
          : { status: 409, body: { detail: 'Ein Rechtsträger mit diesem Namen existiert bereits' } },
    })
    renderApp('/finanzen/eigentuemer')
    const user = userEvent.setup()
    expect(await screen.findByText('Anna privat')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Eigentümer anlegen' }))
    await user.type(await screen.findByLabelText('Name'), 'GbR Ostsee')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Eigentümer angelegt.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Eigentümer anlegen' }))
    await user.type(await screen.findByLabelText('Name'), 'GbR Ostsee')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Ein Rechtsträger mit diesem Namen existiert bereits')).toBeInTheDocument()
  })
})

describe('Accounts', () => {
  it('groups by owner and formats the balance', async () => {
    mockApi({
      ...base,
      'GET /api/accounts': {
        body: [
          account(),
          account({ id: 2, name: 'GbR-Giro', balance: '-12.50', myRole: 'VIEWER', legalEntity: { id: 11, name: 'GbR Ostsee', kind: 'GBR' } }),
        ],
      },
    })
    renderApp('/finanzen/konten')
    const privat = await screen.findByRole('region', { name: 'Anna privat' })
    expect(within(privat).getByText('Hauptkonto')).toBeInTheDocument()
    expect(within(privat).getByText('1.250,00 €')).toBeInTheDocument()
    const gbr = screen.getByRole('region', { name: 'GbR Ostsee' })
    expect(within(gbr).getByText('GbR-Giro')).toBeInTheDocument()
    expect(within(gbr).getByText('Lesen')).toBeInTheDocument()
  })

  it('shows the message for an invalid IBAN', async () => {
    mockApi({
      ...base,
      'GET /api/accounts': { body: [] },
      'GET /api/legal-entities': { body: [{ id: 10, name: 'Anna privat', kind: 'PRIVATE_PERSON', taxNumber: null, personal: true, accountCount: 0 }] },
      'POST /api/accounts': { status: 400, body: { detail: 'Die IBAN ist ungültig' } },
    })
    renderApp('/finanzen/konten')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Konto anlegen' }))
    await user.type(await screen.findByLabelText('Name'), 'Giro')
    await user.type(screen.getByLabelText('IBAN (optional)'), 'DE00')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Die IBAN ist ungültig')).toBeInTheDocument()
  })
})

describe('Transactions', () => {
  const detailMocks = (role: 'OWNER' | 'VIEWER', items = [transaction()]) => ({
    ...base,
    'GET /api/accounts/1': { body: account({ myRole: role }) },
    'GET /api/accounts': { body: [account({ myRole: role })] },
    'GET /api/transactions': { body: page(items) },
  })

  it('sends an expense with a negative amount', async () => {
    let sent: unknown
    mockApi({
      ...detailMocks('OWNER'),
      'POST /api/transactions': (init) => {
        sent = JSON.parse(String(init?.body))
        return { status: 201, body: transaction() }
      },
    })
    renderApp('/finanzen/konten/1')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Transaktion erfassen' }))
    await user.type(await screen.findByLabelText('Betrag (€)'), '84,90')
    await user.type(screen.getByLabelText('Gegenpartei'), 'Edeka')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(sent).toMatchObject({ accountId: 1, amount: '-84.90', counterparty: 'Edeka' }))
  })

  it('shows the message for a concurrent change', async () => {
    mockApi({
      ...detailMocks('OWNER'),
      'PUT /api/transactions/100': { status: 409, body: { detail: 'Die Transaktion wurde inzwischen geändert' } },
    })
    renderApp('/finanzen/konten/1')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Transaktion bearbeiten' }))
    await user.click(await screen.findByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Die Transaktion wurde inzwischen geändert')).toBeInTheDocument()
  })

  it('marks transfers in the table', async () => {
    mockApi(
      detailMocks('OWNER', [
        transaction({ id: 101, amount: '-500.00', counterparty: null, transferId: 'abc', counterAccountId: 2, counterAccountName: 'Tagesgeld' }),
      ]),
    )
    renderApp('/finanzen/konten/1')
    const table = await screen.findByRole('table')
    expect(await within(table).findByText('Umbuchung')).toBeInTheDocument()
    expect(within(table).getByText('nach Tagesgeld')).toBeInTheDocument()
  })

  it('shows a viewer no buttons for entering or editing', async () => {
    mockApi(detailMocks('VIEWER'))
    renderApp('/finanzen/konten/1')
    expect(await screen.findByText('Edeka')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Transaktion erfassen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Transaktion bearbeiten' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Archivieren' })).not.toBeInTheDocument()
  })

  it('shows "Seite nicht gefunden" for an unknown account', async () => {
    mockApi({
      ...base,
      'GET /api/accounts/99': { status: 404, body: { detail: 'Nicht gefunden' } },
      'GET /api/accounts': { body: [] },
      'GET /api/transactions': { status: 404, body: { detail: 'Nicht gefunden' } },
    })
    renderApp('/finanzen/konten/99')
    expect(await screen.findByRole('heading', { name: 'Seite nicht gefunden' })).toBeInTheDocument()
  })

  it('sends the filters as query parameters and shows the totals', async () => {
    const urls: string[] = []
    const fetchMock = mockApi({
      ...base,
      'GET /api/accounts': { body: [account()] },
      'GET /api/transactions': { body: page([transaction()], { income: '1500.00', expense: '-84.90', totalItems: 1 }) },
    })
    renderApp('/finanzen/transaktionen')
    const user = userEvent.setup()
    expect(await screen.findByText('1.500,00 €')).toBeInTheDocument()
    expect(screen.getByLabelText('Summen')).toHaveTextContent('-84,90 €')

    await user.type(screen.getByLabelText('Von'), '2026-10-01')
    await user.click(screen.getByLabelText('Nur ohne Kategorie'))
    await user.type(screen.getByLabelText('Suche'), 'edeka')
    await waitFor(() => {
      fetchMock.mock.calls.forEach(([input]) => urls.push(String(input)))
      expect(urls.some((u) => u.includes('from=2026-10-01') && u.includes('uncategorized=true') && u.includes('q=edeka'))).toBe(true)
    })
  })
})

describe('Sharing', () => {
  const shareMocks = (myRole: 'OWNER' | 'VIEWER', patch?: { status: number; body: unknown }) => ({
    ...base,
    'GET /api/auth/me': authenticated(adminUser),
    'GET /api/accounts/1': { body: account({ myRole }) },
    'GET /api/accounts': { body: [account({ myRole })] },
    'GET /api/transactions': { body: page([]) },
    'GET /api/shares/account/1': {
      body: { myRole, canManage: myRole === 'OWNER', shares: [{ userId: 1, username: 'admin', displayName: 'Anna Admin', role: myRole === 'OWNER' ? 'OWNER' : 'VIEWER' }] },
    },
    'GET /api/users/search': { body: [{ id: 2, username: 'bert', displayName: 'Bert Benutzer' }] },
    'POST /api/shares/account/1': { body: { myRole, canManage: myRole === 'OWNER', shares: [] } },
    ...(patch ? { 'PATCH /api/shares/account/1/1': patch } : {}),
  })

  it('invites a user via the search', async () => {
    const fetchMock = mockApi(shareMocks('OWNER'))
    renderApp('/finanzen/konten/1')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Freigeben' }))
    await user.click(await screen.findByRole('combobox', { name: 'Benutzer einladen' }))
    await user.type(await screen.findByPlaceholderText('Name oder Benutzername'), 'be')
    await user.click(await screen.findByText('Bert Benutzer'))
    await user.click(screen.getByRole('button', { name: 'Einladen' }))
    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([u, init]) => String(u) === '/api/shares/account/1' && init?.method === 'POST')
      expect(JSON.parse(String(call?.[1]?.body))).toEqual({ userId: 2, role: 'VIEWER' })
    })
  })

  it('shows the message for the last owner', async () => {
    mockApi(shareMocks('OWNER', { status: 409, body: { detail: 'Es muss mindestens einen Owner geben' } }))
    renderApp('/finanzen/konten/1')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Freigeben' }))
    await user.click(await screen.findByRole('combobox', { name: 'Rolle von Anna Admin' }))
    await user.click(await screen.findByRole('option', { name: 'Lesen' }))
    expect(await screen.findByText('Es muss mindestens einen Owner geben')).toBeInTheDocument()
  })

  it('shows a viewer only the list', async () => {
    mockApi(shareMocks('VIEWER'))
    renderApp('/finanzen/konten/1')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Freigaben' }))
    const list = await screen.findByRole('list', { name: 'Freigaben' })
    expect(within(list).getByText('Lesen')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Einladen' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Freigabe aufgeben' })).toBeInTheDocument()
  })
})

describe('Category management', () => {
  it('creates categories and shows both error messages', async () => {
    let posts = 0
    mockApi({
      ...base,
      'GET /api/admin/categories': { body: categories },
      'POST /api/admin/categories': () =>
        ++posts === 1 ? { status: 201, body: {} } : { status: 409, body: { detail: 'Eine Kategorie mit diesem Namen existiert bereits' } },
      'DELETE /api/admin/categories/7': { status: 409, body: { detail: 'Die Kategorie wird verwendet und kann nur archiviert werden' } },
    })
    renderApp('/einstellungen/kategorien')
    const user = userEvent.setup()
    const expenses = await screen.findByRole('region', { name: 'Ausgaben' })

    await user.click(within(expenses).getByRole('button', { name: 'Hauptkategorie' }))
    await user.type(await screen.findByLabelText('Name'), 'Hobby')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Kategorie angelegt.')).toBeInTheDocument()

    await user.click(within(expenses).getByRole('button', { name: 'Hauptkategorie' }))
    await user.type(await screen.findByLabelText('Name'), 'Wohnen')
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    expect(await screen.findByText('Eine Kategorie mit diesem Namen existiert bereits')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Abbrechen' }))

    await user.click(within(expenses).getByRole('button', { name: 'Lebensmittel löschen' }))
    expect(await screen.findByText('Die Kategorie wird verwendet und kann nur archiviert werden')).toBeInTheDocument()
  })
})
