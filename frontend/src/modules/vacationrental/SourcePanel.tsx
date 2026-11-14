import { useEffect, useRef } from 'react'
import { MailIcon } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { formatDate } from '@/core/ledger/transactions/format'
import { useBookingSource, type FieldLocation, type SourceRef } from './api'
import { fieldLabels, segments, type ActiveField, type Mark } from './sources'

interface Props {
  bookingId: number
  sources: SourceRef[]
  locations: Record<string, FieldLocation>
  proposalLocations: Record<string, FieldLocation>
  sourceId: number | null
  onSourceChange: (sourceId: number) => void
  active: ActiveField | null
  /** A marked passage was clicked. */
  onMarkClick: (mark: ActiveField) => void
}

function isActive(marks: Mark[], active: ActiveField | null) {
  return active !== null && marks.some((m) => m.field === active.field && m.scope === active.scope)
}

function Marked({ text, marks, active, onMarkClick }: { text: string; marks: Mark[]; active: ActiveField | null; onMarkClick: Props['onMarkClick'] }) {
  const activeRef = useRef<HTMLElement | null>(null)
  const key = active ? `${active.scope}:${active.field}` : ''
  useEffect(() => {
    // jsdom has no layout, hence the guard.
    activeRef.current?.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
  }, [key])
  const parts = segments(text.length, marks)
  const first = parts.findIndex((s) => s.marks.length > 0 && isActive(s.marks, active))
  return (
    <>
      {parts.map((s, i) => {
        const part = text.slice(s.start, s.end)
        if (s.marks.length === 0) return <span key={s.start}>{part}</span>
        const on = isActive(s.marks, active)
        const ref = i === first ? activeRef : undefined
        const target = s.marks.find((m) => active && m.field === active.field && m.scope === active.scope) ?? s.marks[0]
        return (
          <mark
            key={s.start}
            ref={ref}
            data-fields={s.marks.map((m) => m.field).join(' ')}
            data-active={on ? 'true' : undefined}
            title={s.marks.map((m) => (m.scope === 'proposal' ? `${fieldLabels[m.field]} (neu)` : fieldLabels[m.field])).join(', ')}
            className={cn(
              'cursor-pointer rounded-sm px-0.5 text-inherit',
              on ? 'bg-amber-300 ring-2 ring-amber-500 dark:bg-amber-500/60' : 'bg-amber-100 hover:bg-amber-200 dark:bg-amber-500/20',
            )}
            onClick={() => onMarkClick({ field: target.field, scope: target.scope })}
          >
            {part}
          </mark>
        )
      })}
    </>
  )
}

/** The booking's sources side by side with its data: the text a mail was read from, with every value marked. */
export function SourcePanel({ bookingId, sources, locations, proposalLocations, sourceId, onSourceChange, active, onMarkClick }: Props) {
  const current = sources.find((s) => s.id === sourceId) ?? sources[sources.length - 1] ?? null
  const { data: source, isLoading } = useBookingSource(bookingId, current?.id ?? null)

  if (!current) {
    return <p className="text-sm text-muted-foreground">Keine Quelle – die Buchung wurde von Hand erfasst.</p>
  }

  const marks: Mark[] = [
    ...Object.entries(locations).map(([field, location]) => ({ field, scope: 'current' as const, location })),
    ...Object.entries(proposalLocations).map(([field, location]) => ({ field, scope: 'proposal' as const, location })),
  ].filter((m) => m.location.sourceId === current.id)

  return (
    <section aria-label="Quelle" className="space-y-3">
      {sources.length > 1 && (
        <div role="tablist" aria-label="Quellen" className="flex flex-wrap gap-1">
          {sources.map((s) => (
            <button
              key={s.id}
              type="button"
              role="tab"
              aria-selected={s.id === current.id}
              className={cn(
                'rounded-md border px-2.5 py-1 text-xs',
                s.id === current.id ? 'border-primary bg-primary text-primary-foreground' : 'hover:bg-accent',
              )}
              onClick={() => onSourceChange(s.id)}
            >
              {s.label}
              {s.receivedAt && <span className="opacity-70"> · {formatDate(s.receivedAt.slice(0, 10))}</span>}
            </button>
          ))}
        </div>
      )}
      <article className="rounded-md border bg-card" aria-label={current.label}>
        <header className="space-y-1 border-b px-4 py-3 text-sm">
          <div className="flex items-center gap-2 font-medium">
            <MailIcon className="size-4 text-muted-foreground" />
            {current.label}
          </div>
          {source && (
            <dl className="grid grid-cols-[auto_1fr] gap-x-3 text-xs text-muted-foreground">
              {source.sender && (
                <>
                  <dt>Von</dt>
                  <dd className="truncate">{source.sender}</dd>
                </>
              )}
              {source.receivedAt && (
                <>
                  <dt>Datum</dt>
                  <dd>{formatDate(source.receivedAt.slice(0, 10))}</dd>
                </>
              )}
              <dt>Betreff</dt>
              <dd className="text-foreground" aria-label="Betreff">
                <Marked text={source.subject ?? ''} marks={marks.filter((m) => m.location.part === 'SUBJECT')} active={active} onMarkClick={onMarkClick} />
              </dd>
            </dl>
          )}
        </header>
        <div className="max-h-[70vh] overflow-auto px-4 py-3">
          {isLoading || !source ? (
            <div className="space-y-2">
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-4 w-2/3" />
            </div>
          ) : (
            <pre className="font-sans text-sm leading-relaxed whitespace-pre-wrap" aria-label="Mailtext">
              <Marked text={source.text} marks={marks.filter((m) => m.location.part === 'TEXT')} active={active} onMarkClick={onMarkClick} />
            </pre>
          )}
        </div>
      </article>
    </section>
  )
}
