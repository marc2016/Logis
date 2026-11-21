import { formatDate } from '@/core/ledger/transactions/format'
import type { Tenancy } from './api'

/** „01.03.2027 – 31.05.2027“, „seit 01.03.2027“ for a running tenancy without end, „ab …“ for a future one. */
export function period(tenancy: Pick<Tenancy, 'startDate' | 'endDate' | 'state'>) {
  if (tenancy.endDate) return `${formatDate(tenancy.startDate)} – ${formatDate(tenancy.endDate)}`
  return `${tenancy.state === 'FUTURE' ? 'ab' : 'seit'} ${formatDate(tenancy.startDate)}`
}
