import { parseAmount, toInputValue } from '@/lib/money'
import type { Rent, RentInput } from './api'

/** The rent as typed: German amounts as text. */
export interface RentDraft {
  coldRent: string
  utilitiesAdvance: string
  others: { label: string; amount: string }[]
}

export function rentDraft(rent?: Rent | null): RentDraft {
  return {
    coldRent: rent ? toInputValue(rent.coldRent) : '',
    utilitiesAdvance: rent && Number(rent.utilitiesAdvance) !== 0 ? toInputValue(rent.utilitiesAdvance) : '',
    others: rent ? rent.others.map((o) => ({ label: o.label, amount: toInputValue(o.amount) })) : [],
  }
}

function amount(text: string) {
  const parsed = parseAmount(text)
  return parsed === null || parsed.startsWith('-') ? null : parsed
}

/** The typed rent as the API takes it, or the message that tells what is wrong. */
export function parseRent(draft: RentDraft): RentInput | string {
  const coldRent = amount(draft.coldRent)
  if (coldRent === null) return 'Bitte eine gültige Kaltmiete eingeben, z. B. 650,00.'
  const utilitiesAdvance = draft.utilitiesAdvance.trim() ? amount(draft.utilitiesAdvance) : '0.00'
  if (utilitiesAdvance === null) return 'Bitte eine gültige Nebenkosten-Vorauszahlung eingeben, z. B. 150,00.'
  const others: RentInput['others'] = []
  for (const other of draft.others) {
    if (!other.label.trim() && !other.amount.trim()) continue
    const value = amount(other.amount)
    if (!other.label.trim() || value === null) return 'Bitte für jeden weiteren Posten Bezeichnung und Betrag eingeben.'
    others.push({ label: other.label.trim(), amount: value })
  }
  return { coldRent, utilitiesAdvance, others }
}
