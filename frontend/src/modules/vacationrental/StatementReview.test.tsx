import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { Statement } from './api'
import { booking, bookingPage, statement, waldeck } from './fixtures'

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [waldeck] },
}

const settled = booking({ id: 601, arrival: '2027-03-05', departure: '2027-03-12', expectedRent: '760.00', status: 'SETTLED', statementId: 900 })
const openLine = { id: 41, guestName: 'Erika Musterfrau', arrival: '2027-03-20', departure: '2027-03-22', gross: '300.00', commission: '37.49', note: null }

/** An imported statement with one line, one open line and a stated payout. */
function imported(overrides: Partial<Statement> = {}): Statement {
  return statement({
    provider: 'Fewo & Meer',
    periodFrom: '2027-03-01',
    periodTo: '2027-03-31',
    statementDate: '2027-04-03',
    invoiceNumber: 'R-2027-03',
    statedPayout: '715.00',
    payout: '665.00',
    payoutDifference: '50.00',
    lineCount: 1,
    lines: [{ booking: settled, gross: '790.00', commission: '125.00', cleaning: '0.00', net: '665.00' }],
    openLineCount: 1,
    openLines: [openLine],
    toReview: true,
    ...overrides,
  })
}

describe('Imported statements', () => {
  it('marks statements to review in the list', async () => {
    mockApi({ ...base, 'GET /api/vacationrental/statements': { body: [imported(), statement({ id: 901 })] } })
    renderApp('/ferienvermietung/abrechnungen')
    const row = await screen.findByRole('row', { name: /Abrechnung Fewo & Meer/ })
    expect(within(row).getByText('zu prüfen')).toBeInTheDocument()
    expect(within(screen.getByRole('row', { name: /Abrechnung Ferienwohnungen Nord/ })).queryByText('zu prüfen')).not.toBeInTheDocument()
  })

  it('shows invoice number, stated payout, the reasons and lines that differ from the rent', async () => {
    mockApi({ ...base, 'GET /api/vacationrental/statements/900': { body: imported() }, 'GET /api/documents': { body: [] } })
    renderApp('/ferienvermietung/abrechnungen/900')
    const header = await screen.findByLabelText('Wichtige Angaben')
    expect(header).toHaveTextContent('Re-Nr. R-2027-03')
    expect(header).toHaveTextContent('Saldo laut Abrechnung 715,00 €')
    const review = screen.getByRole('status', { name: 'Zu prüfen' })
    expect(review).toHaveTextContent('1 offene Zeile')
    expect(review).toHaveTextContent('Auszahlung weicht um 50,00 € vom Saldo der Abrechnung ab')
    expect(screen.getByText('weicht vom Mietpreis ab (760,00 €)')).toBeInTheDocument()
  })

  it('assigns an open line to the booking with its period and discards another', async () => {
    const sameStay = booking({ id: 602, arrival: '2027-03-20', departure: '2027-03-22' })
    const other = { ...openLine, id: 42, guestName: 'Max Mustermann', note: 'Verlängerungsbuchung' }
    let current = imported({ openLineCount: 2, openLines: [openLine, other] })
    let assigned: unknown
    let discarded = false
    mockApi({
      ...base,
      'GET /api/vacationrental/statements/900': () => ({ body: current }),
      'GET /api/documents': { body: [] },
      'GET /api/vacationrental/bookings': { body: bookingPage([settled, sameStay, booking({ id: 603, statementId: 950, status: 'SETTLED' })]) },
      'POST /api/vacationrental/statements/900/open-lines/41/assign': (init?: RequestInit) => {
        assigned = JSON.parse(String(init?.body))
        current = { ...current, openLines: [other], openLineCount: 1 }
        return { body: current }
      },
      'DELETE /api/vacationrental/statements/900/open-lines/42': () => {
        discarded = true
        current = { ...current, openLines: [], openLineCount: 0, toReview: false, payoutDifference: '0.00' }
        return { body: current }
      },
    })
    renderApp('/ferienvermietung/abrechnungen/900')
    const user = userEvent.setup()
    const section = await screen.findByRole('region', { name: 'Offene Zeilen' })
    expect(within(section).getByText('Verlängerungsbuchung')).toBeInTheDocument()
    await user.click(within(section).getByRole('button', { name: 'Erika Musterfrau zuordnen' }))
    const dialog = await screen.findByRole('dialog', { name: 'Zeile zuordnen' })
    // The booking with the same period is preselected; bookings on other statements are not offered.
    await waitFor(() => expect(within(dialog).getByRole('combobox', { name: 'Buchung' })).toHaveTextContent('20.03.2027 – 22.03.2027'))
    await user.click(within(dialog).getByRole('combobox', { name: 'Buchung' }))
    expect(screen.getAllByRole('option')).toHaveLength(2)
    await user.keyboard('{Escape}')
    await user.click(within(dialog).getByRole('button', { name: 'Zuordnen' }))
    await waitFor(() => expect(assigned).toEqual({ bookingId: 602 }))

    await user.click(await within(section).findByRole('button', { name: 'Max Mustermann verwerfen' }))
    await waitFor(() => expect(discarded).toBe(true))
    expect(await screen.findByText('Zeile verworfen.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Offene Zeilen' })).not.toBeInTheDocument())
    expect(screen.queryByRole('status', { name: 'Zu prüfen' })).not.toBeInTheDocument()
  })

  it('shows viewers only the number of open lines and no documents', async () => {
    mockApi({
      ...base,
      'GET /api/vacationrental/statements/900': { body: imported({ canEdit: false, openLineCount: 2, openLines: [] }) },
    })
    renderApp('/ferienvermietung/abrechnungen/900')
    const section = await screen.findByRole('region', { name: 'Offene Zeilen' })
    expect(section).toHaveTextContent('2 offene Zeilen der Abrechnung sind noch keiner Buchung zugeordnet.')
    expect(within(section).queryByText('Erika Musterfrau')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Dokumente' })).not.toBeInTheDocument()
  })

  it('lists the documents of the statement for editors', async () => {
    mockApi({
      ...base,
      'GET /api/vacationrental/statements/900': { body: imported() },
      'GET /api/documents': {
        body: [{ id: 7, fileName: 'Abrechnung März.pdf', contentType: 'application/pdf', sizeBytes: 2048, createdAt: '2027-04-04T09:00:00Z' }],
      },
    })
    renderApp('/ferienvermietung/abrechnungen/900')
    const documents = await screen.findByRole('region', { name: 'Dokumente' })
    expect(await within(documents).findByText('Abrechnung März.pdf')).toBeInTheDocument()
  })

  it('saves invoice number and stated payout', async () => {
    let sent: Record<string, unknown> | undefined
    mockApi({
      ...base,
      'GET /api/vacationrental/statements/900': { body: statement() },
      'GET /api/documents': { body: [] },
      'GET /api/vacationrental/statements/providers': { body: [] },
      'PUT /api/vacationrental/statements/900': (init?: RequestInit) => {
        sent = JSON.parse(String(init?.body))
        return { body: statement() }
      },
    })
    renderApp('/ferienvermietung/abrechnungen/900')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Bearbeiten' }))
    const dialog = await screen.findByRole('dialog', { name: 'Abrechnung bearbeiten' })
    await user.type(within(dialog).getByLabelText('Rechnungsnummer'), 'R-2026-11')
    await user.type(within(dialog).getByLabelText('Saldo laut Abrechnung (€)'), '1.200,50')
    await user.click(within(dialog).getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(sent).toMatchObject({ invoiceNumber: 'R-2026-11', statedPayout: '1200.50' }))
  })
})
