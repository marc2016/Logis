import { formatDate } from '@/core/ledger/transactions/format'
import type { FetchStatus } from './api'

const number = new Intl.NumberFormat('de-DE')

function mailsLabel(n: number) {
  return `${number.format(n)} ${n === 1 ? 'neue Mail' : 'neue Mails'}`
}

/** What the current or last fetch is doing: a progress bar while it runs, its result afterwards. */
export function FetchProgress({ status }: { status: FetchStatus | null | undefined }) {
  if (!status) return null
  const what = status.kind === 'OLDER' ? `Mails ab ${formatDate(status.from)}` : 'Neue Mails'

  if (status.state === 'RUNNING') {
    const total = status.total
    const text =
      total == null
        ? `${what}: Postfach wird durchsucht …`
        : `${what}: ${number.format(status.checked)} von ${number.format(total)} geprüft, ${number.format(status.created)} neu`
    return (
      <div className="space-y-1.5" role="status" aria-live="polite">
        <p>{text}</p>
        <progress
          className="h-2 w-full overflow-hidden rounded-full [&::-moz-progress-bar]:bg-primary [&::-webkit-progress-bar]:bg-muted [&::-webkit-progress-value]:bg-primary"
          aria-label="Fortschritt des Abrufs"
          max={total || undefined}
          value={total ? status.checked : undefined}
        />
      </div>
    )
  }
  if (status.state === 'FAILED') {
    return (
      <p className="text-destructive" role="alert">
        {status.error ?? 'Abruf fehlgeschlagen'}
        {status.created > 0 && ` (${mailsLabel(status.created)} übernommen)`}
      </p>
    )
  }
  return <p role="status">Abruf abgeschlossen: {mailsLabel(status.created)}</p>
}
