import { formatEUR } from '@/lib/money'
import type { Booking, Statement } from './api'

/** Why a statement is marked „zu prüfen“ (spec rental-statements, „Statements to review“). */
export function reviewReasons(s: Pick<Statement, 'openLineCount' | 'payoutDifference'>): string[] {
  const reasons: string[] = []
  if (s.openLineCount > 0) reasons.push(openLinesText(s.openLineCount))
  // Amounts are decimal strings from the server; only their sign is dropped here.
  if (s.payoutDifference !== null && !/^-?0+(\.0+)?$/.test(s.payoutDifference)) {
    reasons.push(`Auszahlung weicht um ${formatEUR(s.payoutDifference.replace(/^-/, ''))} vom Saldo der Abrechnung ab`)
  }
  return reasons
}

export function openLinesText(count: number) {
  return `${count} offene ${count === 1 ? 'Zeile' : 'Zeilen'}`
}

/** The expected rent if the line's gross differs from it. */
export function rentDifference(gross: string, booking: Pick<Booking, 'expectedRent'>): string | null {
  // Both come from the server with two decimals.
  return booking.expectedRent !== null && booking.expectedRent !== gross ? booking.expectedRent : null
}
