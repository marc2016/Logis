import { useQueries } from '@tanstack/react-query'
import { Link } from 'react-router'
import { useActiveModules } from '@/app/useActiveModules'
import { useInbox } from '@/core/mail/api'
import { useMailLabels } from '@/core/mail/labels'
import { Loading } from '../TileStates'

/** Inbox results that need the user (design D7). */
const INBOX_RESULTS = ['UNASSIGNED_UNIT', 'CHANGE_PROPOSED', 'NOT_RECOGNISED']

/** „Zu erledigen“: what needs attention, each count linking to its list. */
export function TodoTile() {
  const { data: inbox } = useInbox()
  const labels = useMailLabels()
  const { modules } = useActiveModules()
  const sources = modules.flatMap((m) => m.todoSources ?? [])
  const counts = useQueries({ queries: sources.map((s) => s.query()) })
  if (!inbox || counts.some((c) => c.isLoading)) return <Loading />

  const rows = [
    ...INBOX_RESULTS.map((code) => ({
      label: labels.result(code),
      to: `/posteingang?ergebnis=${code}`,
      count: inbox.filter((m) => m.result === code).length,
    })),
    ...sources.map((s, i) => ({ label: s.label, to: s.to, count: counts[i].data ?? 0 })),
  ].filter((r) => r.count > 0)

  if (rows.length === 0) return <p className="text-sm text-muted-foreground">Alles erledigt.</p>
  return (
    <ul className="divide-y">
      {rows.map((r) => (
        <li key={r.to}>
          <Link to={r.to} className="flex items-center justify-between gap-3 py-2 text-sm hover:underline">
            <span>{r.label}</span>
            <span className="rounded-full bg-muted px-2 py-0.5 font-medium tabular-nums">{r.count}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
