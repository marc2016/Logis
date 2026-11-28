import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp } from '@/test/renderApp'
import { account, categories, page, transaction } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': { body: [] },
  'GET /api/categories': { body: categories },
  'GET /api/accounts/1': { body: account() },
  'GET /api/accounts': { body: [account()] },
  'GET /api/properties': { body: [] },
}

const toolbar = () => screen.getByRole('toolbar', { name: 'Aktionen für die Auswahl' })

describe('Bulk actions on transactions', () => {
  it('assigns a category with the full data and version, and reports transfers and conflicts', async () => {
    const items = [
      transaction({ id: 101, counterparty: 'Supermarkt A', version: 3 }),
      transaction({ id: 102, counterparty: 'Supermarkt B' }),
      transaction({ id: 103, transferId: 't-1', counterparty: null, counterAccountName: 'Tagesgeld' }),
      transaction({ id: 104, counterparty: 'Supermarkt C' }),
    ]
    const sent: Record<number, unknown> = {}
    mockApi({
      ...base,
      'GET /api/transactions': { body: page(items) },
      'PUT /api/transactions/101': (init) => ((sent[101] = JSON.parse(String(init?.body))), { body: items[0] }),
      'PUT /api/transactions/102': (init) => ((sent[102] = JSON.parse(String(init?.body))), { body: items[1] }),
      'PUT /api/transactions/103': { status: 400, body: { detail: 'Umbuchungen werden über die Umbuchung bearbeitet.' } },
      'PUT /api/transactions/104': { status: 409, body: { detail: 'Die Transaktion wurde inzwischen geändert' } },
    })
    renderApp('/finanzen/konten/1')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
    await user.click(within(toolbar()).getByRole('button', { name: 'Kategorie zuordnen' }))
    const dialog = await screen.findByRole('dialog', { name: 'Kategorie zuordnen' })
    await user.click(within(dialog).getByRole('combobox'))
    await user.click(await screen.findByRole('option', { name: 'Lebensmittel' }))
    await user.click(within(dialog).getByRole('button', { name: 'Zuordnen' }))
    expect(
      await screen.findByText(
        '2 zugeordnet, 1 nicht: Umbuchungen werden über die Umbuchung bearbeitet., 1 nicht: Die Transaktion wurde inzwischen geändert',
      ),
    ).toBeInTheDocument()
    expect(sent[101]).toMatchObject({ accountId: 1, bookingDate: '2026-10-03', amount: '-84.90', categoryId: 7, counterparty: 'Supermarkt A', version: 3 })
    expect(sent[102]).toMatchObject({ categoryId: 7, version: 0 })
    expect(within(toolbar()).getByText('2 ausgewählt'))
  })

  it('deletes both sides of a transfer with one call', async () => {
    let transferDeletes = 0
    const items = [
      transaction({ id: 103, transferId: 't-1', counterparty: null, counterAccountName: 'Tagesgeld', amount: '-300.00' }),
      transaction({ id: 104, transferId: 't-1', counterparty: null, counterAccountName: 'Girokonto', amount: '300.00' }),
      transaction({ id: 105 }),
    ]
    mockApi({
      ...base,
      'GET /api/transactions': { body: page(items) },
      'DELETE /api/transfers/t-1': () => (transferDeletes++, { status: 204 }),
      'DELETE /api/transactions/105': { status: 204 },
    })
    renderApp('/finanzen/konten/1')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
    await user.click(within(toolbar()).getByRole('button', { name: 'Löschen' }))
    await user.click(within(toolbar()).getByRole('button', { name: 'Ja' }))
    expect(await screen.findByText('2 gelöscht')).toBeInTheDocument()
    expect(transferDeletes).toBe(1)
  })

  it('offers no selection to viewers', async () => {
    mockApi({
      ...base,
      'GET /api/accounts/1': { body: account({ myRole: 'VIEWER' }) },
      'GET /api/accounts': { body: [account({ myRole: 'VIEWER' })] },
      'GET /api/transactions': { body: page([transaction()]) },
    })
    renderApp('/finanzen/konten/1')
    expect(await screen.findByText('Edeka')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: /auswählen/ })).not.toBeInTheDocument()
  })

  it('shows the transaction of a rent payment with its origin and without edit actions, and reports it in bulk', async () => {
    const items = [
      transaction({ id: 201, counterparty: 'Erika Musterfrau', amount: '800.00', source: { type: 'longtermrental.tenancy', id: 600 } }),
      transaction({ id: 202, counterparty: 'Supermarkt' }),
    ]
    mockApi({
      ...base,
      'GET /api/transactions': { body: page(items) },
      'DELETE /api/transactions/201': { status: 409, body: { detail: 'Diese Transaktion gehört zu einer Mietzahlung und wird dort geändert' } },
      'DELETE /api/transactions/202': { status: 204 },
    })
    renderApp('/finanzen/konten/1')
    const user = userEvent.setup()
    const managed = await screen.findByRole('row', { name: /Erika Musterfrau/ })
    expect(within(managed).getByRole('link', { name: 'Mietzahlung' })).toHaveAttribute('href', '/dauervermietung/mietverhaeltnisse/600')
    expect(within(managed).queryByRole('button', { name: 'Transaktion bearbeiten' })).not.toBeInTheDocument()
    expect(within(screen.getByRole('row', { name: /Supermarkt/ })).getByRole('button', { name: 'Transaktion bearbeiten' })).toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: 'Alle auf dieser Seite auswählen' }))
    await user.click(within(toolbar()).getByRole('button', { name: 'Löschen' }))
    await user.click(within(toolbar()).getByRole('button', { name: 'Ja' }))
    expect(await screen.findByText('1 gelöscht, 1 nicht: Diese Transaktion gehört zu einer Mietzahlung und wird dort geändert')).toBeInTheDocument()
  })
})
