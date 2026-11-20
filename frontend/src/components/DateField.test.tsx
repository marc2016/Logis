import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DateField, INVALID_DATE } from './DateField'

function Form({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  return (
    <form aria-label="Formular">
      <label htmlFor="d">Datum</label>
      <DateField id="d" label="Datum" value={value} onChange={setValue} required />
      <output aria-label="Wert">{value}</output>
    </form>
  )
}

const form = () => screen.getByRole('form', { name: 'Formular' }) as HTMLFormElement
const field = () => screen.getByRole('textbox', { name: 'Datum' }) as HTMLInputElement

describe('Required date field', () => {
  it('blocks the form while empty or invalid and passes a valid date', async () => {
    render(<Form />)
    const user = userEvent.setup()
    expect(form().checkValidity()).toBe(false)

    await user.type(field(), '31.02.2030')
    expect(form().checkValidity()).toBe(false)
    expect(field().validationMessage).toBe(INVALID_DATE)
    await user.tab()
    expect(screen.getByRole('alert')).toHaveTextContent(INVALID_DATE)

    await user.clear(field())
    await user.type(field(), '28.02.2030')
    expect(form().checkValidity()).toBe(true)
    expect(screen.getByRole('status', { name: 'Wert' })).toHaveTextContent('2030-02-28')
  })

  it('shows a stored date in German format', () => {
    render(<Form initial="2030-07-01" />)
    expect(field()).toHaveValue('01.07.2030')
    expect(form().checkValidity()).toBe(true)
  })
})
