import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, longTermRentalActive, mockApi, renderApp } from '@/test/renderApp'
import { account } from '@/core/ledger/fixtures'
import type { RentAccount } from './api'
import { rentAccount, rentalUnit, tenancy } from './fixtures'

const PAGE = '/dauervermietung/mietverhaeltnisse/600'
const URL = '/api/longtermrental/tenancies/600'

/** The tenancy page with a rent account the tests can change; returns what was sent. */
function rentApi(initial: RentAccount, preferences: Record<string, unknown> = {}) {
  let current = initial
  const sent: { method: string; url: string; body: unknown }[] = []
  const answer = (method: string, url: string, next: () => RentAccount) => (init?: RequestInit) => {
    sent.push({ method, url, body: init?.body ? JSON.parse(String(init.body)) : null })
    current = next()
    return { body: current }
  }
  mockApi({
    'GET /api/auth/me': authenticated(),
    'GET /api/modules': longTermRentalActive,
    'GET /api/preferences': { body: preferences },
    'PUT /api/preferences/longtermrental.paymentAccount': { status: 204 },
    'GET /api/longtermrental/units': { body: [rentalUnit()] },
    'GET /api/longtermrental/tenancies': { body: [] },
    'GET /api/documents': { body: [] },
    [`GET ${URL}`]: { body: tenancy({ canEdit: initial.canEdit }) },
    [`GET ${URL}/rent-account`]: () => ({ body: current }),
    'GET /api/accounts': {
      body: [account({ id: 5, name: 'Giro' }), account({ id: 6, name: 'Rücklage' }), account({ id: 7, name: 'Fremd', myRole: 'VIEWER' })],
    },
    [`POST ${URL}/payments`]: answer('POST', `${URL}/payments`, () => ({ ...current, paid: '1600.00', arrears: '800.00' })),
    [`PUT ${URL}/payments/31`]: answer('PUT', `${URL}/payments/31`, () => current),
    [`DELETE ${URL}/payments/31`]: answer('DELETE', `${URL}/payments/31`, () => ({ ...current, payments: [] })),
  })
  return sent
}

const section = () => screen.findByRole('region', { name: 'Mietkonto' })

describe('Rent account', () => {
  it('shows totals, months with overdue marks and payments', async () => {
    rentApi(rentAccount())
    renderApp(PAGE)
    const rents = await section()
    const summary = await within(rents).findByLabelText('Stand des Mietkontos')
    expect(summary).toHaveTextContent('Soll2.400,00 €')
    expect(summary).toHaveTextContent('Rückstand1.600,00 €überfällig')
    const months = within(rents).getByRole('table', { name: 'Monate' })
    expect(within(months).getAllByRole('row')[1]).toHaveTextContent('Mai 2027überfällig800,00 €0,00 €800,00 €')
    expect(within(months).getAllByText('überfällig')).toHaveLength(2)
    const payments = within(rents).getByRole('table', { name: 'Zahlungen' })
    expect(payments).toHaveTextContent('03.03.2027')
    expect(payments).toHaveTextContent('Giro')
    expect(payments).toHaveTextContent('Überweisung')
  })

  it('shows a credit', async () => {
    rentApi(rentAccount({ arrears: '0.00', credit: '100.00', overdue: false }))
    renderApp(PAGE)
    expect(await within(await section()).findByLabelText('Stand des Mietkontos')).toHaveTextContent('Guthaben100,00 €')
  })

  it('records a payment into the remembered account', async () => {
    const sent = rentApi(rentAccount(), { 'longtermrental.paymentAccount': 6 })
    renderApp(PAGE)
    const user = userEvent.setup()
    await user.click(await within(await section()).findByRole('button', { name: 'Zahlung erfassen' }))
    const dialog = await screen.findByRole('dialog', { name: 'Zahlung erfassen' })
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Konto' })).toHaveTextContent('Rücklage'))
    // Only accounts the user may book into.
    await user.click(within(dialog).getByRole('combobox', { name: 'Konto' }))
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual([expect.stringContaining('Giro'), expect.stringContaining('Rücklage')])
    await user.keyboard('{Escape}')
    const date = within(dialog).getByRole('textbox', { name: 'Datum' })
    await user.clear(date)
    await user.type(date, '03.04.2027')
    await user.type(within(dialog).getByLabelText('Betrag (€)'), '800,00')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    await waitFor(() =>
      expect(sent).toContainEqual({ method: 'POST', url: `${URL}/payments`, body: { date: '2027-04-03', amount: '800.00', accountId: 6, note: null } }),
    )
    expect(await screen.findByText('Zahlung erfasst.')).toBeInTheDocument()
  })

  it('refuses an amount of zero', async () => {
    const sent = rentApi(rentAccount())
    renderApp(PAGE)
    const user = userEvent.setup()
    await user.click(await within(await section()).findByRole('button', { name: 'Zahlung erfassen' }))
    const dialog = await screen.findByRole('dialog', { name: 'Zahlung erfassen' })
    await user.click(within(dialog).getByRole('combobox', { name: 'Konto' }))
    await user.click(await screen.findByRole('option', { name: /Giro/ }))
    await user.type(within(dialog).getByLabelText('Betrag (€)'), '0')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    expect(await within(dialog).findByText(/Betrag größer als 0/)).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })

  it('changes and deletes a payment', async () => {
    const sent = rentApi(rentAccount())
    renderApp(PAGE)
    const user = userEvent.setup()
    const rents = await section()
    await user.click(await within(rents).findByRole('button', { name: 'Zahlung vom 03.03.2027 bearbeiten' }))
    const dialog = await screen.findByRole('dialog', { name: 'Zahlung bearbeiten' })
    expect(within(dialog).getByLabelText('Betrag (€)')).toHaveValue('800,00')
    expect(within(dialog).getByRole('combobox', { name: 'Konto' })).toHaveTextContent('Giro')
    await user.clear(within(dialog).getByLabelText('Betrag (€)'))
    await user.type(within(dialog).getByLabelText('Betrag (€)'), '750')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(sent[0]).toMatchObject({ method: 'PUT', body: { amount: '750.00', accountId: 5, version: 0 } }))

    await user.click(within(rents).getByRole('button', { name: 'Zahlung vom 03.03.2027 löschen' }))
    await waitFor(() => expect(sent[1]).toMatchObject({ method: 'DELETE' }))
  })

  it('shows viewers the account without account names or actions', async () => {
    rentApi(rentAccount({ canEdit: false, payments: [{ id: 31, date: '2027-03-03', amount: '800.00', accountId: null, accountName: null, note: null, version: 0 }] }))
    renderApp(PAGE)
    const rents = await section()
    expect(await within(rents).findByRole('table', { name: 'Zahlungen' })).toHaveTextContent('800,00 €')
    expect(within(rents).queryByText('Konto')).not.toBeInTheDocument()
    expect(within(rents).queryByRole('button', { name: 'Zahlung erfassen' })).not.toBeInTheDocument()
  })
})
