import { describe, expect, it } from 'vitest'
import { formatEUR, parseAmount, toInputValue } from './money'

describe('formatEUR', () => {
  it('formats in German with a thousands separator', () => {
    expect(formatEUR('-84.90')).toBe('-84,90 €')
    expect(formatEUR('1250.00')).toBe('1.250,00 €')
    expect(formatEUR('0.00')).toBe('0,00 €')
    expect(formatEUR('999999999.99')).toBe('999.999.999,99 €')
  })

  it('loses no precision for large amounts', () => {
    expect(formatEUR('123456789012.35')).toBe('123.456.789.012,35 €')
  })
})

describe('parseAmount', () => {
  it('reads German input', () => {
    expect(parseAmount('1.234,56')).toBe('1234.56')
    expect(parseAmount('-84,9')).toBe('-84.90')
    expect(parseAmount('12')).toBe('12.00')
    expect(parseAmount(' 84,90 € ')).toBe('84.90')
    expect(parseAmount('999.999.999,99')).toBe('999999999.99')
  })

  it('rejects invalid input', () => {
    expect(parseAmount('0,005')).toBeNull()
    expect(parseAmount('abc')).toBeNull()
    expect(parseAmount('12.34')).toBeNull() // a dot as decimal separator is ambiguous
    expect(parseAmount('1.23.4')).toBeNull()
    expect(parseAmount('1000000000,00')).toBeNull()
    expect(parseAmount('')).toBeNull()
  })

  it('never uses floating point', () => {
    expect(parseAmount('0,1')).toBe('0.10')
    expect(parseAmount('0,2')).toBe('0.20')
  })
})

describe('toInputValue', () => {
  it('returns the amount without sign for input fields', () => {
    expect(toInputValue('-84.90')).toBe('84,90')
    expect(toInputValue('1234.56')).toBe('1234,56')
  })
})
