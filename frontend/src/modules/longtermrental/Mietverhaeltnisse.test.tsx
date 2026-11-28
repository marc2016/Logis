import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, longTermRentalActive, mockApi, renderApp } from '@/test/renderApp'
import { rentalUnit, tenancy, whg2 } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': longTermRentalActive,
  'GET /api/preferences': { body: {} },
  'GET /api/longtermrental/units': { body: [rentalUnit()] },
  'GET /api/documents': { body: [] },
}

const urls = (fetch: ReturnType<typeof mockApi>) => fetch.mock.calls.map((c) => String(c[0]))

describe('Mietverhältnisse', () => {
  it('lists running and future tenancies by default', async () => {
    const fetch = mockApi({
      ...base,
      'GET /api/longtermrental/tenancies': {
        body: [
          tenancy(),
          tenancy({ id: 601, unit: whg2, state: 'FUTURE', startDate: '2027-01-01', tenants: [{ ...tenancy().tenants[0], name: 'Max Mustermann' }] }),
        ],
      },
    })
    renderApp('/dauervermietung/mietverhaeltnisse')
    const row = await screen.findByRole('row', { name: /Erika Musterfrau/ })
    expect(row).toHaveTextContent('Haus Talblick · Whg. 1')
    expect(row).toHaveTextContent('seit 01.03.2026')
    expect(row).toHaveTextContent('800,00 €')
    expect(row).toHaveTextContent('laufend')
    const future = screen.getByRole('row', { name: /Max Mustermann/ })
    expect(future).toHaveTextContent('ab 01.01.2027')
    expect(future).toHaveTextContent('künftig')
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Laufend und künftig')
    expect(urls(fetch)).toContain('/api/longtermrental/tenancies?state=RUNNING&state=FUTURE')
  })

  it('takes the filters from the address', async () => {
    const fetch = mockApi({
      ...base,
      'GET /api/longtermrental/tenancies': {
        body: [tenancy({ state: 'ENDED', endDate: '2026-05-31', tenants: [], tenantsDeleted: true })],
      },
    })
    renderApp('/dauervermietung/mietverhaeltnisse?propertyId=8&unitId=81&state=ENDED')
    const row = await screen.findByRole('row', { name: /Mieterdaten gelöscht/ })
    expect(row).toHaveTextContent('01.03.2026 – 31.05.2026')
    expect(row).toHaveTextContent('beendet')
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Beendet')
    expect(screen.getByRole('combobox', { name: 'Objekt' })).toHaveTextContent('Haus Talblick')
    expect(screen.getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Talblick · Whg. 1')
    expect(urls(fetch)).toContain('/api/longtermrental/tenancies?propertyId=8&unitId=81&state=ENDED')
  })

  it('opens a tenancy as its own page and returns with the filters', async () => {
    mockApi({
      ...base,
      'GET /api/longtermrental/tenancies': { body: [tenancy()] },
      'GET /api/longtermrental/tenancies/600': { body: tenancy() },
    })
    const { router } = renderApp('/dauervermietung/mietverhaeltnisse?state=all')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Erika Musterfrau/ }))
    expect(await screen.findByRole('heading', { name: 'Erika Musterfrau' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/dauervermietung/mietverhaeltnisse/600')
    expect(screen.getByRole('link', { name: /Zurück zu Mietverhältnissen/ })).toHaveAttribute('href', '/dauervermietung/mietverhaeltnisse?state=all')
  })

  it('records a tenancy with two tenants and a rent', async () => {
    let created: Record<string, unknown> | undefined
    mockApi({
      ...base,
      'GET /api/longtermrental/tenancies': { body: [] },
      'POST /api/longtermrental/tenancies': (init) => {
        created = JSON.parse(String(init?.body))
        return { status: 201, body: tenancy({ id: 610 }) }
      },
      'GET /api/longtermrental/tenancies/610': { body: tenancy({ id: 610 }) },
    })
    const { router } = renderApp('/dauervermietung/mietverhaeltnisse')
    const user = userEvent.setup()
    expect(await screen.findByText('Keine Mietverhältnisse.')).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Mietverhältnis' }))
    const dialog = await screen.findByRole('dialog', { name: 'Mietverhältnis anlegen' })
    // The only unit is preselected.
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Talblick · Whg. 1'))
    const start = within(dialog).getByRole('textbox', { name: 'Beginn' })
    await user.clear(start)
    await user.type(start, '01.03.2027')
    const first = within(dialog).getByRole('group', { name: 'Mieter 1' })
    await user.type(within(first).getByRole('textbox', { name: 'Name' }), 'Erika Musterfrau')
    await user.type(within(first).getByRole('textbox', { name: 'E-Mail' }), 'erika@example.org')
    await user.click(within(dialog).getByRole('button', { name: 'Weiterer Mieter' }))
    await user.type(within(within(dialog).getByRole('group', { name: 'Mieter 2' })).getByRole('textbox', { name: 'Name' }), 'Max Mustermann')
    await user.type(within(dialog).getByRole('textbox', { name: 'Kaltmiete (€)' }), '650')
    await user.type(within(dialog).getByRole('textbox', { name: 'Nebenkosten-Vorauszahlung (€)' }), '150,00')
    await user.click(within(dialog).getByRole('button', { name: 'Weiterer Posten' }))
    await user.type(within(dialog).getByRole('textbox', { name: 'Bezeichnung' }), 'Stellplatz')
    await user.type(within(dialog).getByRole('textbox', { name: 'Betrag (€)' }), '40')
    await user.type(within(dialog).getByRole('textbox', { name: 'Kaution (€)' }), '1.950,00')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(created).toBeDefined())
    expect(created).toMatchObject({
      unitId: 81,
      startDate: '2027-03-01',
      endDate: null,
      depositAmount: '1950.00',
      depositReceivedOn: null,
      rent: { coldRent: '650.00', utilitiesAdvance: '150.00', others: [{ label: 'Stellplatz', amount: '40.00' }] },
    })
    const tenants = created!.tenants as { name: string; email: string | null }[]
    expect(tenants.map((t) => t.name)).toEqual(['Erika Musterfrau', 'Max Mustermann'])
    expect(tenants[0].email).toBe('erika@example.org')
    // The new tenancy opens as its page.
    await waitFor(() => expect(router.state.location.pathname).toBe('/dauervermietung/mietverhaeltnisse/610'))
  })

  it('shows the error messages of the backend and of the form', async () => {
    mockApi({
      ...base,
      'GET /api/longtermrental/tenancies': { body: [] },
      'POST /api/longtermrental/tenancies': { status: 409, body: { detail: 'Die Einheit ist in diesem Zeitraum bereits vermietet' } },
    })
    renderApp('/dauervermietung/mietverhaeltnisse?neu=81')
    const user = userEvent.setup()
    const dialog = await screen.findByRole('dialog', { name: 'Mietverhältnis anlegen' })
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Talblick · Whg. 1'))
    await user.type(within(dialog).getByRole('textbox', { name: 'Name' }), 'Erika Musterfrau')
    await user.type(within(dialog).getByRole('textbox', { name: 'Kaltmiete (€)' }), 'viel')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    expect(await within(dialog).findByText('Bitte eine gültige Kaltmiete eingeben, z. B. 650,00.')).toBeInTheDocument()
    await user.clear(within(dialog).getByRole('textbox', { name: 'Kaltmiete (€)' }))
    await user.type(within(dialog).getByRole('textbox', { name: 'Kaltmiete (€)' }), '650,00')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    expect(await within(dialog).findByText('Die Einheit ist in diesem Zeitraum bereits vermietet')).toBeInTheDocument()
  })

  it('offers no new tenancy without a unit to edit', async () => {
    mockApi({
      ...base,
      'GET /api/longtermrental/units': { body: [rentalUnit({ canEdit: false })] },
      'GET /api/longtermrental/tenancies': { body: [tenancy({ canEdit: false })] },
    })
    renderApp('/dauervermietung/mietverhaeltnisse')
    expect(await screen.findByRole('row', { name: /Erika Musterfrau/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mietverhältnis' })).not.toBeInTheDocument()
  })

  it('shows the arrears of each tenancy, marked when overdue', async () => {
    mockApi({
      ...base,
      'GET /api/longtermrental/tenancies': {
        body: [
          tenancy({ id: 601, arrears: '800.00', overdue: true }),
          tenancy({ id: 602, unit: whg2, tenants: [{ ...tenancy().tenants[0], id: 2, name: 'Max Mustermann' }] }),
        ],
      },
    })
    renderApp('/dauervermietung/mietverhaeltnisse')
    const behind = await screen.findByRole('row', { name: /Erika Musterfrau/ })
    expect(within(behind).getByText('800,00 € Rückstand')).toHaveAttribute('data-overdue', 'true')
    expect(screen.getByRole('row', { name: /Max Mustermann/ })).not.toHaveTextContent('Rückstand')
  })
})
