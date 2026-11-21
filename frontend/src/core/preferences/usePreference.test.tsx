import { describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { mockApi } from '@/test/renderApp'
import { usePreference } from './usePreference'

function View() {
  const [view, setView, loaded] = usePreference('vacationrental.bookingView', 'full')
  return (
    <>
      <output aria-label="Ansicht">{view}</output>
      <output aria-label="Geladen">{String(loaded)}</output>
      <button type="button" onClick={() => setView(view === 'full' ? 'compact' : 'full')}>
        Umschalten
      </button>
    </>
  )
}

function renderView() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <View />
    </QueryClientProvider>,
  )
}

const view = () => screen.getByRole('status', { name: 'Ansicht' })

describe('usePreference', () => {
  it('uses the default without a stored setting and the stored value once loaded', async () => {
    mockApi({ 'GET /api/preferences': { body: { 'vacationrental.bookingView': 'compact' } } })
    renderView()
    expect(view()).toHaveTextContent('full')
    await waitFor(() => expect(view()).toHaveTextContent('compact'))
    expect(screen.getByRole('status', { name: 'Geladen' })).toHaveTextContent('true')
  })

  it('applies a change at once and saves it', async () => {
    const saved: unknown[] = []
    mockApi({
      'GET /api/preferences': { body: {} },
      'PUT /api/preferences/vacationrental.bookingView': (init) => {
        saved.push(JSON.parse(String(init?.body)))
        return { status: 204 }
      },
    })
    renderView()
    await waitFor(() => expect(screen.getByRole('status', { name: 'Geladen' })).toHaveTextContent('true'))
    await userEvent.setup().click(screen.getByRole('button', { name: 'Umschalten' }))
    expect(view()).toHaveTextContent('compact')
    await waitFor(() => expect(saved).toEqual(['compact']))
  })

  it('keeps the change on the page when saving fails', async () => {
    mockApi({
      'GET /api/preferences': { body: {} },
      'PUT /api/preferences/vacationrental.bookingView': { status: 500, body: { detail: 'kaputt' } },
    })
    renderView()
    await waitFor(() => expect(screen.getByRole('status', { name: 'Geladen' })).toHaveTextContent('true'))
    await userEvent.setup().click(screen.getByRole('button', { name: 'Umschalten' }))
    expect(view()).toHaveTextContent('compact')
  })

  it('uses the default when the settings cannot be loaded', async () => {
    mockApi({ 'GET /api/preferences': { status: 500, body: { detail: 'kaputt' } } })
    renderView()
    await waitFor(() => expect(screen.getByRole('status', { name: 'Geladen' })).toHaveTextContent('true'))
    expect(view()).toHaveTextContent('full')
  })
})
