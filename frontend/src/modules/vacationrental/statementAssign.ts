import { api } from '@/lib/api'
import { parseAmount } from '@/lib/money'
import type { Booking, Statement } from './api'

/** Commission in euros from gross and a rate in percent, rounded to cents: ("248.58", 15) -> "37.29". */
export function commissionFor(gross: string, ratePercent: number) {
  const cents = Math.round(Number(gross) * ratePercent)
  return (cents / 100).toFixed(2)
}

/** "15" or "15,5" -> 15.5; null for anything else or a rate outside 0..100. */
export function parseRate(input: string): number | null {
  const text = input.trim().replace('%', '').replace(',', '.')
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(text)) return null
  const rate = Number(text)
  return rate <= 100 ? rate : null
}

/** One booking onto a statement: expected rent as gross, commission from the rate, the same cleaning for all. */
export function assignLine(statementId: number, booking: Booking, ratePercent: number, cleaning: string) {
  const gross = booking.expectedRent
  return api<Statement>(`/api/vacationrental/statements/${statementId}/lines/${booking.id}`, {
    method: 'PUT',
    json: { gross, commission: gross ? commissionFor(gross, ratePercent) : null, cleaning },
  })
}

export interface AssignAmounts {
  rate: number
  cleaning: string
}

/** Checks the amount fields; returns the values or an error message. */
export function readAmounts(rate: string, cleaning: string): AssignAmounts | string {
  const r = parseRate(rate || '0')
  if (r === null) return 'Bitte eine Provision zwischen 0 und 100 % eingeben.'
  const c = parseAmount(cleaning || '0')
  if (c === null || c.startsWith('-')) return 'Bitte einen gültigen Betrag für die Reinigung eingeben, z. B. 60,00.'
  return { rate: r, cleaning: c }
}
