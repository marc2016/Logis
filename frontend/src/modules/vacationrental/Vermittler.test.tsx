import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { authenticated, mockApi, renderApp, vacationRentalActive } from '@/test/renderApp'
import { categories } from '@/core/ledger/fixtures'
import type { Agency } from './api'
import { seeufer } from './fixtures'

const vvv: Agency = {
  id: 5,
  name: 'VVV-Stadt- und Citymarketing Nordhorn e.V.',
  archived: false,
  version: 2,
  aliases: [{ id: 50, alias: 'VVV-Stadt- und Citymarketing Nordhorn e.V.' }],
  bookingCount: 1,
}
const holidu: Agency = { id: 6, name: 'Holidu', archived: false, version: 0, aliases: [{ id: 60, alias: 'Holidu' }], bookingCount: 3 }
const holiduDe: Agency = { id: 7, name: 'holidu.de', archived: false, version: 0, aliases: [{ id: 70, alias: 'holidu.de' }], bookingCount: 1 }

const base = {
  'GET /api/auth/me': authenticated(),
  'GET /api/modules': vacationRentalActive,
  'GET /api/categories': { body: categories },
  'GET /api/vacationrental/units': { body: [seeufer] },
}

describe('Agencies page', () => {
  it('renames an agency with its version', async () => {
    let body: Record<string, unknown> | undefined
    mockApi({
      ...base,
      'GET /api/vacationrental/agencies': { body: [vvv, holidu] },
      'PUT /api/vacationrental/agencies/5': (init) => {
        body = JSON.parse(String(init?.body))
        return { body: { ...vvv, name: 'VVV Nordhorn' } }
      },
    })
    renderApp('/ferienvermietung/vermittler')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: `${vvv.name} bearbeiten` }))
    const dialog = await screen.findByRole('dialog')
    const name = within(dialog).getByLabelText('Name')
    await user.clear(name)
    await user.type(name, 'VVV Nordhorn')
    await user.click(within(dialog).getByRole('button', { name: 'Umbenennen' }))
    await waitFor(() => expect(body).toEqual({ name: 'VVV Nordhorn', version: 2 }))
  })

  it('merges an agency into another one', async () => {
    let merged: Record<string, unknown> | undefined
    mockApi({
      ...base,
      'GET /api/vacationrental/agencies': { body: [holidu, holiduDe] },
      'POST /api/vacationrental/agencies/7/merge': (init) => {
        merged = JSON.parse(String(init?.body))
        return { body: holidu }
      },
    })
    renderApp('/ferienvermietung/vermittler')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'holidu.de bearbeiten' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('combobox', { name: 'Zusammenführen mit' }))
    await user.click(await screen.findByRole('option', { name: 'Holidu' }))
    await user.click(within(dialog).getByRole('button', { name: 'Zusammenführen' }))
    await user.click(within(dialog).getByRole('button', { name: 'Ja' }))
    await waitFor(() => expect(merged).toEqual({ targetId: 6 }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
})
