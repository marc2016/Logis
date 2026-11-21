import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, longTermRentalActive, mockApi, renderApp } from '@/test/renderApp'
import { rentalUnit, tenancy, whg2 } from './fixtures'

const whg3 = { ...whg2, id: 83, name: 'Whg. 3' }
const whg4 = { ...whg2, id: 84, name: 'Whg. 4' }

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': longTermRentalActive,
  'GET /api/preferences': { body: {} },
  'GET /api/longtermrental/tenancies': { body: [] },
  'GET /api/longtermrental/units': {
    body: [
      rentalUnit({ tenancy: tenancy() }),
      rentalUnit({ unit: whg2, vacantSince: '2026-06-01' }),
      rentalUnit({ unit: whg3 }),
      rentalUnit({ unit: whg4, vacantSince: '2026-06-01', next: tenancy({ id: 620, unit: whg4, state: 'FUTURE', startDate: '2027-01-01' }) }),
    ],
  },
}

const card = (name: string) => screen.getByRole('heading', { name }).closest('li')!

describe('Einheiten', () => {
  it('shows the tenant and rent of a rented unit', async () => {
    mockApi(base)
    renderApp('/dauervermietung/einheiten')
    expect(await screen.findByRole('heading', { name: 'Haus Talblick · Whg. 1' })).toBeInTheDocument()
    const rented = card('Haus Talblick · Whg. 1')
    expect(within(rented).getByRole('link', { name: 'Erika Musterfrau' })).toHaveAttribute('href', '/dauervermietung/mietverhaeltnisse/600')
    expect(rented).toHaveTextContent('Miete 800,00 €')
    expect(rented).not.toHaveTextContent('Leerstand')
  })

  it('shows the vacancy with its day, or without when never rented', async () => {
    mockApi(base)
    renderApp('/dauervermietung/einheiten')
    expect(await screen.findByText('Leerstand seit 01.06.2026', { selector: 'li:nth-child(2) p' })).toBeInTheDocument()
    expect(within(card('Haus Talblick · Whg. 3')).getByText('Leerstand')).toBeInTheDocument()
    // A future tenancy does not end the vacancy and is shown next to it.
    const waiting = card('Haus Talblick · Whg. 4')
    expect(waiting).toHaveTextContent('Leerstand seit 01.06.2026')
    expect(within(waiting).getByRole('link', { name: 'ab 01.01.2027 vermietet' })).toHaveAttribute('href', '/dauervermietung/mietverhaeltnisse/620')
  })

  it('leads to a new tenancy with the unit preselected', async () => {
    mockApi(base)
    const { router } = renderApp('/dauervermietung/einheiten')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('link', { name: 'Mietverhältnis für Haus Talblick · Whg. 3 anlegen' }))
    const dialog = await screen.findByRole('dialog', { name: 'Mietverhältnis anlegen' })
    expect(router.state.location.pathname).toBe('/dauervermietung/mietverhaeltnisse')
    expect(within(dialog).getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Talblick · Whg. 3')
  })

  it('offers no new tenancy to a viewer and explains an empty module', async () => {
    mockApi({ ...base, 'GET /api/longtermrental/units': { body: [rentalUnit({ canEdit: false })] } })
    const { router } = renderApp('/dauervermietung/einheiten')
    expect(await screen.findByText('Leerstand')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /anlegen/ })).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/dauervermietung/einheiten')
  })
})
