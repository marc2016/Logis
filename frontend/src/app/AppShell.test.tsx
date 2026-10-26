import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { authenticated, mockApi, normalUser, renderApp, vacationRentalActive } from '@/test/renderApp'

describe('App-Shell', () => {
  it('zeigt ein aktives Modul als eigene Gruppe', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': vacationRentalActive })
    renderApp('/')
    expect(await screen.findByText('Ferienvermietung')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Belegung/ })).toHaveAttribute('href', '/ferienvermietung/belegung')
  })

  it('blendet ein deaktiviertes Modul aus', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': { body: [] } })
    renderApp('/')
    expect(await screen.findByRole('link', { name: /Übersicht/ })).toBeInTheDocument()
    expect(screen.queryByText('Ferienvermietung')).not.toBeInTheDocument()
  })

  it('zeigt "Benutzer" nur für Admins', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(normalUser), 'GET /api/modules': vacationRentalActive })
    renderApp('/')
    expect(await screen.findByText('Ferienvermietung')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Benutzer$/ })).not.toBeInTheDocument()
  })

  it('zeigt "Benutzer" für Admins', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': vacationRentalActive })
    renderApp('/')
    expect(await screen.findByRole('link', { name: /^Benutzer$/ })).toHaveAttribute('href', '/einstellungen/benutzer')
  })

  it('hebt den aktiven Eintrag hervor', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': vacationRentalActive })
    renderApp('/ferienvermietung/belegung')
    const link = await screen.findByRole('link', { name: /Belegung/ })
    expect(link).toHaveAttribute('data-active', 'true')
    expect(screen.getByRole('link', { name: /Übersicht/ })).toHaveAttribute('data-active', 'false')
  })
})

describe('Routing', () => {
  it('zeigt die Modulseite bei aktivem Modul', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': vacationRentalActive })
    renderApp('/ferienvermietung/belegung')
    expect(await screen.findByRole('heading', { name: 'Belegung' })).toBeInTheDocument()
  })

  it('zeigt "Seite nicht gefunden" für ein deaktiviertes Modul', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(), 'GET /api/modules': { body: [] } })
    renderApp('/ferienvermietung/belegung')
    expect(await screen.findByRole('heading', { name: 'Seite nicht gefunden' })).toBeInTheDocument()
  })

  it('zeigt "Seite nicht gefunden" für die Benutzerverwaltung als USER', async () => {
    mockApi({ 'GET /api/auth/me': authenticated(normalUser), 'GET /api/modules': { body: [] } })
    renderApp('/einstellungen/benutzer')
    expect(await screen.findByRole('heading', { name: 'Seite nicht gefunden' })).toBeInTheDocument()
  })
})
