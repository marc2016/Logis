import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { Overview } from './api'
import { booking, bookingPage, seeufer, statement } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [seeufer] },
}

function pdf(name = 'Bestätigung.pdf') {
  return new File(['%PDF-1.7'], name, { type: 'application/pdf' })
}

describe('Bookings grid', () => {
  it('sends the sort when a column header is clicked', async () => {
    const fetch = mockApi({ ...base, 'GET /api/vacationrental/bookings': { body: bookingPage([booking()]) } })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    expect(await screen.findByText('Max Gast')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Anreise/ }))
    await waitFor(() => expect(fetch.mock.calls.map((c) => String(c[0]))).toContainEqual(expect.stringContaining('sort=arrival%2Casc')))
  })

  it('takes filters and sorting from the URL', async () => {
    const fetch = mockApi({ ...base, 'GET /api/vacationrental/bookings': { body: bookingPage([]) } })
    renderApp('/ferienvermietung/buchungen?status=PLANNED&q=275505&sort=nights,asc')
    expect(await screen.findByText('Keine Buchungen gefunden.')).toBeInTheDocument()
    const url = fetch.mock.calls.map((c) => String(c[0])).find((u) => u.startsWith('/api/vacationrental/bookings'))!
    expect(url).toContain('status=PLANNED')
    expect(url).toContain('q=275505')
    expect(url).toContain('sort=nights%2Casc')
    expect(screen.getByRole('columnheader', { name: /Nächte/ })).toHaveAttribute('aria-sort', 'ascending')
  })

  it('opens the dialog with the button at the top right', async () => {
    mockApi({ ...base, 'GET /api/vacationrental/bookings': { body: bookingPage([]) } })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Buchung' }))
    expect(await screen.findByRole('heading', { name: 'Buchung erfassen' })).toBeInTheDocument()
  })
})

describe('Agencies on bookings', () => {
  const agency = { id: 3, name: 'Fewo & Meer', archived: false, version: 0, aliases: [{ id: 30, alias: 'Fewo & Meer' }], bookingCount: 1 }

  it('creates a new agency from the booking dialog and saves it on the booking', async () => {
    let createdAgency: Record<string, unknown> | undefined
    let created: Record<string, unknown> | undefined
    mockApi({
      ...base,
      'GET /api/vacationrental/agencies': { body: [agency] },
      'GET /api/vacationrental/bookings': { body: bookingPage([]) },
      'POST /api/vacationrental/agencies': (init) => {
        createdAgency = JSON.parse(String(init?.body))
        return { status: 201, body: { id: 4, name: 'Ferienbüro Nord', archived: false, version: 0, aliases: [], bookingCount: 0 } }
      },
      'POST /api/vacationrental/bookings': (init) => {
        created = JSON.parse(String(init?.body))
        return { status: 201, body: booking({ id: 503 }) }
      },
    })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Buchung' }))
    const dialog = await screen.findByRole('dialog')
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Seeufer'))
    expect(within(dialog).queryByText('Kanal')).not.toBeInTheDocument()
    await user.click(within(dialog).getByRole('combobox', { name: /Vermittler/ }))
    await user.type(await screen.findByPlaceholderText('Vermittler suchen oder anlegen'), 'Ferienbüro Nord')
    await user.click(await screen.findByRole('option', { name: /„Ferienbüro Nord“ anlegen/ }))
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: /Vermittler/ })).toHaveTextContent('Ferienbüro Nord'))
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(created).toMatchObject({ agencyId: 4 }))
    expect(createdAgency).toEqual({ name: 'Ferienbüro Nord' })
    expect(created).not.toHaveProperty('channel')
  })

  it('shows the agency name in the list and filters by agency', async () => {
    const fetch = mockApi({
      ...base,
      'GET /api/vacationrental/agencies': { body: [agency] },
      'GET /api/vacationrental/bookings': { body: bookingPage([booking({ agency: { id: 3, name: 'Fewo & Meer', archived: false } })]) },
    })
    renderApp('/ferienvermietung/buchungen?agencyId=3')
    expect(await screen.findByRole('cell', { name: 'Fewo & Meer' })).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: /Kanal/ })).not.toBeInTheDocument()
    const url = fetch.mock.calls.map((c) => String(c[0])).find((u) => u.startsWith('/api/vacationrental/bookings'))!
    expect(url).toContain('agencyId=3')
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Vermittler' })).toHaveTextContent('Fewo & Meer'))
  })
})

describe('Booking dialog', () => {
  it('uploads files chosen on create after saving', async () => {
    const uploads: string[] = []
    let created: Record<string, unknown> | undefined
    mockApi({
      ...base,
      'GET /api/vacationrental/bookings': { body: bookingPage([]) },
      'POST /api/vacationrental/bookings': (init) => {
        created = JSON.parse(String(init?.body))
        return { status: 201, body: booking({ id: 501 }) }
      },
      'POST /api/documents': (init) => {
        const body = init?.body as FormData
        uploads.push(`${body.get('ownerType')}:${body.get('ownerId')}:${(body.get('file') as File).name}`)
        return { status: 201, body: { id: 1, fileName: 'Bestätigung.pdf', contentType: 'application/pdf', sizeBytes: 8, createdAt: '' } }
      },
    })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Buchung' }))
    const dialog = await screen.findByRole('dialog')
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Seeufer'))
    await user.type(within(dialog).getByLabelText('Buchungsnummer'), '275505')
    await user.upload(within(dialog).getByLabelText('Dokument auswählen'), pdf())
    expect(within(dialog).getByText('Bestätigung.pdf')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(uploads).toEqual(['vacationrental.booking:501:Bestätigung.pdf']))
    expect(created).toMatchObject({ unitId: 70, externalRef: '275505' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('keeps the saved booking when an upload fails', async () => {
    mockApi({
      ...base,
      'GET /api/vacationrental/bookings': { body: bookingPage([]) },
      'POST /api/vacationrental/bookings': { status: 201, body: booking({ id: 502 }) },
      'POST /api/documents': { status: 400, body: { detail: 'Nur PDF- oder Bilddateien bis 10 MB' } },
      'GET /api/documents': { body: [] },
    })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Buchung' }))
    const dialog = await screen.findByRole('dialog')
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Einheit' })).toHaveTextContent('Haus Seeufer'))
    await user.upload(within(dialog).getByLabelText('Dokument auswählen'), pdf('kaputt.pdf'))
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    expect(await within(dialog).findByText('kaputt.pdf: Nur PDF- oder Bilddateien bis 10 MB')).toBeInTheDocument()
    expect(within(dialog).getByRole('heading', { name: 'Buchung bearbeiten' })).toBeInTheDocument()
  })

  it('shows viewers neither contact data nor documents', async () => {
    const viewerBooking = booking({
      canEdit: false,
      guest: { name: 'Max Gast', street: null, postalCode: null, city: null, country: null, phone: null, email: null },
    })
    mockApi({
      ...base,
      'GET /api/vacationrental/units': { body: [{ ...seeufer, myRole: 'VIEWER' }] },
      'GET /api/vacationrental/bookings': { body: bookingPage([viewerBooking]) },
    })
    renderApp('/ferienvermietung/buchungen')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('row', { name: /Buchung 275505/ }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: 'Buchung' })).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Max Gast')
    expect(within(dialog).queryByLabelText('E-Mail')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Telefon')).not.toBeInTheDocument()
    expect(within(dialog).queryByText('Dokumente')).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Buchung' })).not.toBeInTheDocument()
  })
})

describe('Statements', () => {
  it('shows the net per line and the payout after assigning a booking', async () => {
    let assigned = false
    const settledLine = {
      booking: booking({ status: 'SETTLED', statementId: 900 }),
      gross: '248.58',
      commission: '37.29',
      cleaning: '60.00',
      net: '151.29',
    }
    mockApi({
      ...base,
      'GET /api/vacationrental/statements': { body: [statement()] },
      'GET /api/vacationrental/statements/900': () => ({
        body: assigned ? statement({ payout: '151.29', lineCount: 1, lines: [settledLine] }) : statement(),
      }),
      'GET /api/vacationrental/bookings': { body: bookingPage([booking()]) },
      'PUT /api/vacationrental/statements/900/lines/500': (init) => {
        assigned = true
        expect(JSON.parse(String(init?.body))).toEqual({ gross: '248.58', commission: '37.29', cleaning: '60.00' })
        return { body: statement({ payout: '151.29', lineCount: 1, lines: [settledLine] }) }
      },
    })
    renderApp('/ferienvermietung/abrechnungen?statement=900')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Buchung zuordnen' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('combobox', { name: 'Buchung' }))
    await user.click(await screen.findByRole('option', { name: /275505/ }))
    expect(within(dialog).getByLabelText('Brutto (€)')).toHaveValue('248,58')
    await user.type(within(dialog).getByLabelText('Provision (€)'), '37,29')
    await user.type(within(dialog).getByLabelText('Reinigung (€)'), '60,00')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))

    const section = await screen.findByRole('region', { name: 'Abrechnung' })
    expect(await within(section).findByText('151,29 €', { selector: 'td:not([data-testid]) span' })).toBeInTheDocument()
    expect(within(screen.getByTestId('payout')).getByText('151,29 €')).toBeInTheDocument()
  })
})

describe('Overview', () => {
  it('renders nights, occupancy and money per month', async () => {
    const months = Array.from({ length: 12 }, (_, i) => ({ month: i + 1, nights: 0, occupancy: 0, settledNet: '0.00', plannedRent: '0.00' }))
    months[10] = { month: 11, nights: 11, occupancy: 37, settledNet: '540.00', plannedRent: '248.58' }
    const overview: Overview = { unitId: 70, year: 2026, months, total: { month: 0, nights: 11, occupancy: 3, settledNet: '540.00', plannedRent: '248.58' } }
    mockApi({ ...base, 'GET /api/vacationrental/occupancy/overview': { body: overview } })
    renderApp('/ferienvermietung/auswertung?unitId=70&year=2026')
    const november = await screen.findByRole('row', { name: /November/ })
    expect(within(november).getByText('11')).toBeInTheDocument()
    expect(within(november).getByText('37 %')).toBeInTheDocument()
    expect(within(november).getByText('540,00 €')).toBeInTheDocument()
    expect(within(november).getByText('248,58 €')).toBeInTheDocument()
  })
})
