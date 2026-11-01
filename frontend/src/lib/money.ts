/**
 * Money amounts come from the server as decimal strings ("-84.90"). This only formats them and
 * converts user input into that format - all arithmetic happens on the server.
 */

const formatter = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

/** "-84.90" -> "-84,90 €" */
export function formatEUR(amount: string): string {
  const match = /^(-?)(\d+)(?:\.(\d{1,2}))?$/.exec(amount.trim())
  if (!match) return amount
  const [, sign, whole, fraction = ''] = match
  // The integer part must not go through Number (precision); do the grouping ourselves.
  const grouped = whole.replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  const cents = fraction.padEnd(2, '0')
  const symbol = formatter.formatToParts(0).find((p) => p.type === 'currency')?.value ?? '€'
  return `${sign}${grouped},${cents} ${symbol}`
}

export function isNegative(amount: string): boolean {
  return amount.trim().startsWith('-')
}

/**
 * German input ("1.234,56", "84,9", "-12") -> "1234.56" / "84.90" / "-12.00".
 * Returns null for invalid input or more than two decimal places.
 */
export function parseAmount(input: string): string | null {
  const text = input.trim().replace(/\s| |€/g, '')
  const match = /^(-?)(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?$/.exec(text)
  if (!match) return null
  const [, sign, whole, fraction = ''] = match
  const digits = whole.replace(/\./g, '').replace(/^0+(?=\d)/, '')
  if (digits.length > 9) return null
  return `${sign}${digits}.${fraction.padEnd(2, '0')}`
}

/** "-84.90" -> "84,90" for input fields (without sign and thousands separators). */
export function toInputValue(amount: string): string {
  return amount.replace(/^-/, '').replace('.', ',')
}
