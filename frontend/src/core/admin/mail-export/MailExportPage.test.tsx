import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp } from '@/test/renderApp'
import { WARNING } from './MailExportPage'

const mailboxes = [
  { ownerUserId: 2, username: 'erika', displayName: 'Erika Musterfrau' },
  { ownerUserId: 3, username: 'max', displayName: 'Max Beispiel' },
]

const log = [
  {
    id: 1,
    at: '2026-11-19T09:30:00Z',
    adminName: 'Anna Admin',
    ownerUserId: 2,
    ownerName: 'Erika Musterfrau',
    mailCount: 42,
    filters: '{"owners":[2],"from":"2026-11-01","to":null,"results":["NOT_RECOGNISED"],"sources":["Fewo & Meer"]}',
  },
]

function setup(extra: Parameters<typeof mockApi>[0] = {}) {
  return mockApi({
    'GET /api/auth/me': authenticated(),
    'GET /api/modules': { body: [] },
    'GET /api/admin/mail-export/mailboxes': { body: mailboxes },
    'GET /api/admin/mail-export/agencies': { body: ['Fewo & Meer', 'Holidu'] },
    'GET /api/admin/mail-export/log': { body: log },
    ...extra,
  })
}

describe('Mail export', () => {
  const createObjectURL = vi.fn(() => 'blob:export')
  beforeEach(() => {
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = vi.fn()
    createObjectURL.mockClear()
  })
  afterEach(() => vi.restoreAllMocks())

  it('lists consenting mailboxes and the log', async () => {
    setup()
    renderApp('/einstellungen/mail-export')
    expect(await screen.findByRole('checkbox', { name: 'Postfach von Erika Musterfrau' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Postfach von Max Beispiel' })).toBeChecked()
    const table = await screen.findByRole('table', { name: 'Protokoll' })
    const row = within(table).getAllByRole('row')[1]
    expect(row).toHaveTextContent('Erika Musterfrau')
    expect(row).toHaveTextContent('42')
    expect(row).toHaveTextContent('01.11.2026 – … · Nicht erkannt · Fewo & Meer')
    expect(row).toHaveTextContent('Anna Admin')
  })

  it('says so when no mailbox consents', async () => {
    setup({ 'GET /api/admin/mail-export/mailboxes': { body: [] }, 'GET /api/admin/mail-export/log': { body: [] } })
    renderApp('/einstellungen/mail-export')
    expect(await screen.findByText('Kein Postfach ist für den Export freigegeben.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Exportieren' })).not.toBeInTheDocument()
  })

  it('exports with the chosen filters after the warning', async () => {
    let sent: unknown
    const fetchMock = setup({
      'POST /api/admin/mail-export': (init) => {
        sent = JSON.parse(String(init?.body))
        return { status: 200 }
      },
    })
    renderApp('/einstellungen/mail-export')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('checkbox', { name: 'Postfach von Max Beispiel' }))
    await user.click(screen.getByRole('checkbox', { name: 'Nicht erkannt' }))
    await user.click(await screen.findByRole('checkbox', { name: 'Fewo & Meer' }))

    // „Abbrechen“ in the warning sends nothing.
    await user.click(screen.getByRole('button', { name: 'Exportieren' }))
    expect(await screen.findByText(WARNING)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)

    await user.click(screen.getByRole('button', { name: 'Exportieren' }))
    await user.click(await screen.findByRole('button', { name: 'Herunterladen' }))
    await waitFor(() => expect(createObjectURL).toHaveBeenCalled())
    expect(sent).toEqual({ owners: [2], from: null, to: null, results: ['NOT_RECOGNISED'], sources: ['Fewo & Meer'] })
  })

  it('shows the message when too many mails match', async () => {
    setup({
      'POST /api/admin/mail-export': {
        status: 400,
        body: { detail: 'Zu viele Mails für einen Export. Bitte den Zeitraum oder die Filter einschränken.' },
      },
    })
    renderApp('/einstellungen/mail-export')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Exportieren' }))
    await user.click(await screen.findByRole('button', { name: 'Herunterladen' }))
    expect(await screen.findByText(/Zu viele Mails für einen Export/)).toBeInTheDocument()
    expect(createObjectURL).not.toHaveBeenCalled()
  })
})
