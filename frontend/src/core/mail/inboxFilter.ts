import type { SortState } from '@/components/DataTable'
import { toIso } from '@/components/dateInput'
import type { InboxItem } from './api'

/** Result filter value for all mails that need attention. */
export const OPEN = 'offen'
export const DEFAULT_SORT = 'receivedAt,desc'

/** Inbox filters as kept in the URL; empty strings mean "no filter". */
export interface InboxFilter {
  result: string
  kind: string
  sender: string
  from: string
  to: string
  q: string
}

interface Labels {
  result: (code: string) => string
  kind: (code: string | null) => string
}

/** What the sender column shows: the agency or other source, else the address. */
export function senderOf(m: InboxItem): string {
  return m.source ?? m.sender ?? '–'
}

export function parseSort(value: string | null): SortState {
  const [id, dir] = (value || DEFAULT_SORT).split(',')
  return { id, desc: dir !== 'asc' }
}

export function filterInbox(items: InboxItem[], f: InboxFilter): InboxItem[] {
  const q = f.q.trim().toLowerCase()
  return items.filter((m) => {
    if (f.result === OPEN ? !m.needsAttention : f.result && m.result !== f.result) return false
    if (f.kind && m.kind !== f.kind) return false
    if (f.sender && senderOf(m) !== f.sender) return false
    const day = toIso(new Date(m.receivedAt))
    if (f.from && day < f.from) return false
    if (f.to && day > f.to) return false
    return !q || (m.subject ?? '').toLowerCase().includes(q)
  })
}

const collator = new Intl.Collator('de', { sensitivity: 'base', numeric: true })

/** Sorts by what the column shows, so „Art“ and „Ergebnis“ follow their German labels. */
export function sortInbox(items: InboxItem[], sort: SortState, labels: Labels): InboxItem[] {
  const value = (m: InboxItem): string => {
    switch (sort.id) {
      case 'source':
        return senderOf(m)
      case 'subject':
        return m.subject ?? ''
      case 'kind':
        return m.kind ? labels.kind(m.kind) : ''
      case 'result':
        return labels.result(m.result)
      default:
        return m.receivedAt
    }
  }
  const direction = sort.desc ? -1 : 1
  return [...items].sort((a, b) => {
    const byValue = sort.id === 'receivedAt' ? value(a).localeCompare(value(b)) : collator.compare(value(a), value(b))
    // Ties keep the newest mail first.
    return byValue * direction || b.receivedAt.localeCompare(a.receivedAt) || b.id - a.id
  })
}
