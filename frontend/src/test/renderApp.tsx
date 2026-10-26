import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { vi } from 'vitest'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'
import { routes } from '@/app/routes'

export type MockResponse = { status?: number; body?: unknown }
type Handler = MockResponse | ((init: RequestInit | undefined) => MockResponse)

/** Simuliert die API: Schluessel "METHODE /pfad". */
export function mockApi(handlers: Record<string, Handler>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    const key = `${method} ${String(input)}`
    // Exakter Treffer inkl. Query-String, sonst nur der Pfad.
    const handler = handlers[key] ?? handlers[`${method} ${String(input).split('?')[0]}`]
    if (!handler) return new Response(JSON.stringify({ detail: `kein Mock für ${key}` }), { status: 500 })
    const { status = 200, body } = typeof handler === 'function' ? handler(init) : handler
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export const adminUser = { id: 1, username: 'admin', displayName: 'Anna Admin', role: 'ADMIN' }
export const normalUser = { id: 2, username: 'bert', displayName: 'Bert Benutzer', role: 'USER' }

export function authenticated(user = adminUser) {
  return { status: 200, body: { stage: 'AUTHENTICATED', user, recoveryCodes: null } }
}

export const vacationRentalActive = { body: [{ id: 'vacationrental', label: 'Ferienvermietung', order: 100 }] }

export function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <RouterProvider router={router} />
        <Toaster />
      </TooltipProvider>
    </QueryClientProvider>,
  )
  return { router }
}
