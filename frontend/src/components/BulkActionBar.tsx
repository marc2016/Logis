import type { ReactNode } from 'react'
import { LoaderCircleIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { Selection } from './useSelection'

/** Bar at the bottom of a list while entries are selected: count, actions and „Auswahl aufheben“. */
export function BulkActionBar({ selection, busy, children }: { selection: Selection; busy?: boolean; children: ReactNode }) {
  const count = selection.ids.size
  if (count === 0) return null
  return (
    <div
      role="toolbar"
      aria-label="Aktionen für die Auswahl"
      className="sticky bottom-4 z-20 mt-4 flex flex-wrap items-center gap-2 rounded-lg border bg-background/95 p-2 shadow-lg backdrop-blur"
    >
      <span className="px-2 text-sm font-medium" aria-live="polite">
        {count} ausgewählt
      </span>
      {busy && <LoaderCircleIcon className="size-4 animate-spin text-muted-foreground" aria-label="Wird ausgeführt" />}
      <div className="flex flex-wrap items-center gap-2">{children}</div>
      <Button type="button" variant="ghost" size="sm" className="ml-auto" disabled={busy} onClick={selection.clear}>
        Auswahl aufheben
      </Button>
    </div>
  )
}
