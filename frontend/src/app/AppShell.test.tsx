import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { authenticated, longTermRentalActive, mockApi, normalUser, renderApp, vacationRentalActive } from '@/test/renderApp'

describe('App shell', () => {
  it('shows an active module as its own group', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': vacationRentalActive })
    renderApp('/')
    expect(await screen.findByText('Ferienvermietung')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Buchungen/ })).toHaveAttribute('href', '/ferienvermietung/buchungen')
    expect(screen.getByRole('link', { name: /Kalender/ })).toHaveAttribute('href', '/ferienvermietung/kalender')
    expect(screen.getByRole('link', { name: /Abrechnungen/ })).toHaveAttribute('href', '/ferienvermietung/abrechnungen')
    expect(screen.getByRole('link', { name: /Auswertung/ })).toHaveAttribute('href', '/ferienvermietung/auswertung')
  })

  it('shows long-term rental only while that module is active', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': longTermRentalActive })
    renderApp('/')
    expect(await screen.findByText('Dauervermietung')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Mietverhältnisse/ })).toHaveAttribute('href', '/dauervermietung/mietverhaeltnisse')
    expect(screen.getByRole('link', { name: /Einheiten/ })).toHaveAttribute('href', '/dauervermietung/einheiten')
    expect(screen.queryByText('Ferienvermietung')).not.toBeInTheDocument()
  })

  it('hides a disabled module', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': { body: [] } })
    renderApp('/')
    expect(await screen.findByRole('link', { name: /Übersicht/ })).toBeInTheDocument()
    expect(screen.queryByText('Ferienvermietung')).not.toBeInTheDocument()
    expect(screen.queryByText('Dauervermietung')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Mietverhältnisse/ })).not.toBeInTheDocument()
  })

  it('shows "Benutzer" only to admins', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(normalUser), 'GET /api/modules': vacationRentalActive })
    renderApp('/')
    expect(await screen.findByText('Ferienvermietung')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Benutzer$/ })).not.toBeInTheDocument()
  })

  it('shows "Benutzer" to admins', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': vacationRentalActive })
    renderApp('/')
    expect(await screen.findByRole('link', { name: /^Benutzer$/ })).toHaveAttribute('href', '/einstellungen/benutzer')
  })

  it('highlights the active entry', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': vacationRentalActive })
    renderApp('/ferienvermietung/buchungen')
    const link = await screen.findByRole('link', { name: /Buchungen/ })
    expect(link).toHaveAttribute('data-active', 'true')
    expect(screen.getByRole('link', { name: /Übersicht/ })).toHaveAttribute('data-active', 'false')
  })
})

describe('Routing', () => {
  it('shows the module page for an active module', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': vacationRentalActive })
    renderApp('/ferienvermietung/buchungen')
    expect(await screen.findByRole('heading', { name: 'Buchungen' })).toBeInTheDocument()
  })

  it('shows "Seite nicht gefunden" for a disabled module', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': { body: [] } })
    renderApp('/ferienvermietung/buchungen')
    expect(await screen.findByRole('heading', { name: 'Seite nicht gefunden' })).toBeInTheDocument()
  })

  it('shows "Seite nicht gefunden" for the pages of disabled long-term rental', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': vacationRentalActive })
    renderApp('/dauervermietung/mietverhaeltnisse')
    expect(await screen.findByRole('heading', { name: 'Seite nicht gefunden' })).toBeInTheDocument()
  })

  it('shows "Seite nicht gefunden" for user management as USER', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(normalUser), 'GET /api/modules': { body: [] } })
    renderApp('/einstellungen/benutzer')
    expect(await screen.findByRole('heading', { name: 'Seite nicht gefunden' })).toBeInTheDocument()
  })

  it('asks users without an e-mail address to add one', async () => {
    const me = authenticated()
    mockApi({
      'GET /api/auth/me': { ...me, body: { ...me.body, user: { ...me.body.user, emailMissing: true } } },
      'GET /api/modules': { body: [] },
    })
    renderApp('/')
    expect(await screen.findByText(/Bitte hinterlege eine E-Mail-Adresse unter/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Mein Konto' })).toHaveAttribute('href', '/konto')
  })

  it('shows no notice when the address exists', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': { body: [] } })
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Übersicht' })).toBeInTheDocument()
    expect(screen.queryByText(/Bitte hinterlege eine E-Mail-Adresse/)).not.toBeInTheDocument()
  })
})
