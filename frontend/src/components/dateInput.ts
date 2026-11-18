/** Parsing and arithmetic for ISO dates (`yyyy-MM-dd`) entered as `TT.MM.JJJJ`. */

const pad = (n: number) => String(n).padStart(2, '0')

export function toIso(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function fromIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** `TT.MM.JJJJ` (day and month also with one digit) as ISO date; null if incomplete or not a real date. */
export function parseGermanDate(text: string): string | null {
  const match = /^\s*(\d{1,2})\.(\d{1,2})\.(\d{4})\s*$/.exec(text)
  if (!match) return null
  const [day, month, year] = match.slice(1).map(Number)
  const date = new Date(year, month - 1, day)
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null
  return toIso(date)
}

/** The same day `years` years earlier; 29 February becomes 28 February. */
export function minusYears(iso: string, years: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  const last = new Date(y - years, m, 0).getDate()
  return `${y - years}-${pad(m)}-${pad(Math.min(d, last))}`
}
