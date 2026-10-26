import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'

// jsdom kennt matchMedia/ResizeObserver nicht (Sidebar, Radix).
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }),
})

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver

// Radix Select nutzt Pointer-Capture-APIs.
Element.prototype.hasPointerCapture ??= () => false
Element.prototype.scrollIntoView ??= () => {}
// input-otp prueft Passwort-Manager-Badges per elementFromPoint.
document.elementFromPoint ??= () => null

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  document.cookie.split('; ').forEach((c) => {
    document.cookie = `${c.split('=')[0]}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  })
})
