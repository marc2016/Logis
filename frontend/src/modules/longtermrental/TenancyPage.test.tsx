import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, longTermRentalActive, mockApi, renderApp } from '@/test/renderApp'
import type { Tenancy } from './api'
import { rent, rentalUnit, tenancy } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': longTermRentalActive,
  'GET /api/preferences': { body: {} },
  'GET /api/longtermrental/units': { body: [rentalUnit()] },
  'GET /api/longtermrental/tenancies': { body: [] },
  'GET /api/documents': { body: [] },
}

const PAGE = '/dauervermietung/mietverhaeltnisse/600'

/** A tenancy the tests can change through the API; returns what was sent. */
function tenancyApi(initial: Tenancy) {
  const state = { current: initial }
  const sent: { url: string; method: string; body: unknown }[] = []
  const record = (method: string, url: string, next: (body: Record<string, unknown>) => Tenancy) => ({
    [`${method} ${url}`]: (init: RequestInit | undefined) => {
      const body = init?.body ? JSON.parse(String(init.body)) : null
      sent.push({ url, method, body })
      state.current = next(body)
      return { body: state.current }
    },
  })
  const url = `/api/longtermrental/tenancies/${initial.id}`
  mockApi({
    ...base,
    [`GET ${url}`]: () => ({ body: state.current }),
    ...record('POST', `${url}/rent-terms`, (body) => {
      const term = rent({ id: 901, validFrom: body.validFrom as string, coldRent: body.coldRent as string, total: '830.00' })
      return { ...state.current, rentTerms: [...state.current.rentTerms, term], currentRent: term, version: state.current.version + 1 }
    }),
    ...record('PUT', url, (body) => ({ ...state.current, endDate: body.endDate as string | null, state: 'ENDED', version: state.current.version + 1 })),
    ...record('DELETE', `${url}/tenant-data`, () => ({ ...state.current, tenants: [], tenantsDeleted: true })),
  })
  return sent
}

describe('Tenancy page', () => {
  it('shows header, tenants, rent, deposit and documents', async () => {
    tenancyApi(tenancy({ depositAmount: '1950.00', rentTerms: [rent({ others: [{ label: 'Stellplatz', amount: '40.00' }], total: '840.00' })] }))
    renderApp(PAGE)
    expect(await screen.findByRole('heading', { name: 'Erika Musterfrau' })).toBeInTheDocument()
    const facts = screen.getByLabelText('Wichtige Angaben')
    expect(facts).toHaveTextContent('Haus Talblick · Whg. 1')
    expect(facts).toHaveTextContent('seit 01.03.2026')
    expect(facts).toHaveTextContent('Miete 800,00 €')
    expect(facts).toHaveTextContent('laufend')
    const tenants = screen.getByRole('region', { name: 'Mieter' })
    expect(tenants).toHaveTextContent('Musterweg 1')
    expect(within(tenants).getByRole('link', { name: 'erika@example.org' })).toHaveAttribute('href', 'mailto:erika@example.org')
    const history = screen.getByRole('table', { name: 'Mietverlauf' })
    expect(history).toHaveTextContent('Stellplatz 40,00 €')
    expect(history).toHaveTextContent('840,00 €')
    // A deposit without a received date is open.
    expect(screen.getByRole('region', { name: 'Kaution' })).toHaveTextContent('1.950,00 € offen')
    expect(screen.getByRole('region', { name: 'Dokumente' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mieterdaten löschen' })).not.toBeInTheDocument()
  })

  it('adds a history row with „Miete ändern“ and shows the new current rent', async () => {
    const sent = tenancyApi(tenancy())
    renderApp(PAGE)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Miete ändern' }))
    const dialog = await screen.findByRole('dialog', { name: 'Miete ändern' })
    // The latest rent is the starting point.
    expect(within(dialog).getByRole('textbox', { name: 'Kaltmiete (€)' })).toHaveValue('650,00')
    expect(within(dialog).getByRole('textbox', { name: 'Nebenkosten-Vorauszahlung (€)' })).toHaveValue('150,00')
    await user.type(within(dialog).getByRole('textbox', { name: 'Gültig ab' }), '01.03.2027')
    await user.clear(within(dialog).getByRole('textbox', { name: 'Kaltmiete (€)' }))
    await user.type(within(dialog).getByRole('textbox', { name: 'Kaltmiete (€)' }), '680')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(sent).toHaveLength(1))
    expect(sent[0].body).toEqual({ validFrom: '2027-03-01', coldRent: '680.00', utilitiesAdvance: '150.00', others: [] })
    await waitFor(() => expect(within(screen.getByRole('table', { name: 'Mietverlauf' })).getAllByRole('row')).toHaveLength(3))
    expect(screen.getByRole('table', { name: 'Mietverlauf' })).toHaveTextContent('01.03.2027')
    expect(screen.getByLabelText('Wichtige Angaben')).toHaveTextContent('Miete 830,00 €')
    // The first rent can be corrected but not deleted.
    expect(screen.getByRole('button', { name: 'Miete ab 01.03.2026 korrigieren' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Miete ab 01.03.2026 löschen' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Miete ab 01.03.2027 löschen' })).toBeInTheDocument()
  })

  it('shows the message for a date that already has a rent', async () => {
    mockApi({
      ...base,
      'GET /api/longtermrental/tenancies/600': { body: tenancy() },
      'POST /api/longtermrental/tenancies/600/rent-terms': { status: 409, body: { detail: 'Für dieses Datum gibt es bereits eine Miete' } },
    })
    renderApp(PAGE)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Miete ändern' }))
    const dialog = await screen.findByRole('dialog', { name: 'Miete ändern' })
    await user.type(within(dialog).getByRole('textbox', { name: 'Gültig ab' }), '01.03.2026')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    expect(await within(dialog).findByText('Für dieses Datum gibt es bereits eine Miete')).toBeInTheDocument()
  })

  it('ends a tenancy and offers „Mieterdaten löschen“ only after the end', async () => {
    const sent = tenancyApi(tenancy({ depositAmount: '1950.00', depositReceivedOn: '2026-03-05' }))
    renderApp(PAGE)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Beenden' }))
    const dialog = await screen.findByRole('dialog', { name: 'Mietverhältnis beenden' })
    await user.type(within(dialog).getByRole('textbox', { name: 'Ende (letzter Tag)' }), '31.05.2026')
    await user.click(within(dialog).getByRole('button', { name: 'Beenden' }))

    await waitFor(() => expect(sent).toHaveLength(1))
    // Everything else stays as it is.
    expect(sent[0].body).toMatchObject({ unitId: 81, startDate: '2026-03-01', endDate: '2026-05-31', depositAmount: '1950.00', depositReceivedOn: '2026-03-05', version: 0 })
    expect((sent[0].body as { tenants: { name: string }[] }).tenants[0].name).toBe('Erika Musterfrau')
    await waitFor(() => expect(screen.getByLabelText('Wichtige Angaben')).toHaveTextContent('01.03.2026 – 31.05.2026'))
    expect(screen.getByLabelText('Wichtige Angaben')).toHaveTextContent('beendet')
    expect(screen.queryByRole('button', { name: 'Beenden' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Mieterdaten löschen' }))
    await user.click(within(screen.getByRole('group', { name: /endgültig löschen/ })).getByRole('button', { name: 'Ja' }))
    expect(await screen.findByRole('heading', { name: 'Mieterdaten gelöscht' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Mieter' })).toHaveTextContent('Mieterdaten gelöscht')
    expect(screen.queryByRole('button', { name: 'Mieterdaten löschen' })).not.toBeInTheDocument()
    // Period and rent stay.
    expect(screen.getByRole('table', { name: 'Mietverlauf' })).toHaveTextContent('650,00 €')
  })

  it('changes dates, deposit and tenants with „Bearbeiten“', async () => {
    const sent = tenancyApi(tenancy())
    renderApp(PAGE)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Bearbeiten' }))
    const dialog = await screen.findByRole('dialog', { name: 'Mietverhältnis bearbeiten' })
    expect(within(dialog).getByRole('textbox', { name: 'Name' })).toHaveValue('Erika Musterfrau')
    // The rent is changed in the history, not here.
    expect(within(dialog).queryByRole('textbox', { name: 'Kaltmiete (€)' })).not.toBeInTheDocument()
    await user.type(within(dialog).getByRole('textbox', { name: 'Kaution (€)' }), '1950')
    await user.type(within(dialog).getByRole('textbox', { name: 'Kaution erhalten am' }), '05.03.2026')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(sent).toHaveLength(1))
    expect(sent[0].body).toMatchObject({ depositAmount: '1950.00', depositReceivedOn: '2026-03-05', version: 0 })
    expect(sent[0].body).not.toHaveProperty('rent')
  })

  it('deletes the tenancy after a confirmation and returns to the list', async () => {
    let deleted = false
    mockApi({
      ...base,
      'GET /api/longtermrental/tenancies/600': { body: tenancy() },
      'DELETE /api/longtermrental/tenancies/600': () => {
        deleted = true
        return { status: 204 }
      },
    })
    const { router } = renderApp(PAGE)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Löschen' }))
    expect(deleted).toBe(false)
    await user.click(screen.getByRole('button', { name: 'Ja' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/dauervermietung/mietverhaeltnisse'))
    expect(deleted).toBe(true)
  })

  it('shows a viewer neither contact data nor ways to edit', async () => {
    const viewed = tenancy({
      canEdit: false,
      tenants: [{ id: 1, name: 'Erika Musterfrau', street: null, postalCode: null, city: null, country: null, phone: null, email: null }],
      depositAmount: '1950.00',
      depositReceivedOn: '2026-03-05',
    })
    const fetch = mockApi({ ...base, 'GET /api/longtermrental/tenancies/600': { body: viewed } })
    renderApp(PAGE)
    expect(await screen.findByRole('heading', { name: 'Erika Musterfrau' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Mieter' })).toHaveTextContent(/^MieterErika Musterfrau$/)
    expect(screen.getByRole('region', { name: 'Kaution' })).toHaveTextContent('1.950,00 € erhalten am 05.03.2026')
    ;['Bearbeiten', 'Beenden', 'Löschen', 'Miete ändern', 'Miete ab 01.03.2026 korrigieren'].forEach((name) =>
      expect(screen.queryByRole('button', { name }), name).not.toBeInTheDocument(),
    )
    expect(screen.queryByRole('region', { name: 'Dokumente' })).not.toBeInTheDocument()
    expect(fetch.mock.calls.map((c) => String(c[0])).some((u) => u.startsWith('/api/documents'))).toBe(false)
  })

  it('says „Mietverhältnis nicht gefunden“ for an unknown or invisible tenancy', async () => {
    mockApi({ ...base, 'GET /api/longtermrental/tenancies/600': { status: 404, body: { detail: 'Mietverhältnis nicht gefunden' } } })
    renderApp(PAGE)
    expect(await screen.findByRole('alert')).toHaveTextContent('Mietverhältnis nicht gefunden')
    expect(screen.getByRole('link', { name: /Zurück zu Mietverhältnissen/ })).toHaveAttribute('href', '/dauervermietung/mietverhaeltnisse')
  })
})
