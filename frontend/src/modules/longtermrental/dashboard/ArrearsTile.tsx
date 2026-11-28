import { Link } from 'react-router'
import { Loading } from '@/core/dashboard/TileStates'
import { formatEUR } from '@/lib/money'
import { tenancyPath } from '../paths'
import { tenantNames, unitLabel, useTenancies } from '../api'

/** „Mietrückstände“: tenancies with an overdue month, largest arrears first (spec dashboard, „Tile catalog“). */
export function ArrearsTile() {
  const { data } = useTenancies({ states: [] })
  if (!data) return <Loading />
  // Only for the order; the amounts themselves are shown as the server sent them.
  const overdue = data.filter((t) => t.overdue).sort((a, b) => Number(b.arrears) - Number(a.arrears))
  if (overdue.length === 0) return <p className="text-sm text-muted-foreground">Keine Mietrückstände.</p>
  return (
    <ul className="divide-y text-sm">
      {overdue.map((t) => (
        <li key={t.id}>
          <Link to={tenancyPath(t.id)} className="flex items-center gap-3 py-1.5 hover:underline">
            <span className="min-w-0 flex-1 truncate">
              {tenantNames(t) || 'Mieter'}
              <span className="text-muted-foreground"> · {unitLabel(t.unit)}</span>
            </span>
            <span className="shrink-0 font-medium tabular-nums text-destructive">{formatEUR(t.arrears)}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
