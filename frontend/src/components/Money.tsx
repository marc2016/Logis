import { cn } from '@/lib/utils'
import { formatEUR, isNegative } from '@/lib/money'

/** Betrag mit Vorzeichenfarbe; tabellarische Ziffern fuer saubere Spalten. */
export function Money({ amount, className, colored = true }: { amount: string; className?: string; colored?: boolean }) {
  return (
    <span
      className={cn(
        'tabular-nums whitespace-nowrap',
        colored && (isNegative(amount) ? 'text-red-700' : amount.replace(/[0.]/g, '') ? 'text-emerald-700' : ''),
        className,
      )}
    >
      {formatEUR(amount)}
    </span>
  )
}
