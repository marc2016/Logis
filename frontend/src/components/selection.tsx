import { Checkbox } from '@/components/ui/checkbox'
import type { Selection } from './useSelection'

/** Checkbox of one row; a click never reaches the row (which would open it). */
export function RowCheckbox({ id, label, selection, order }: { id: string; label: string; selection: Selection; order: string[] }) {
  return (
    <span className="inline-flex" onClick={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
      <Checkbox
        aria-label={`${label} auswählen`}
        checked={selection.isSelected(id)}
        onClick={(e) => {
          e.preventDefault()
          selection.toggle(id, e.shiftKey, order)
        }}
      />
    </span>
  )
}

/** Header checkbox: selects or clears all selectable rows of the page. */
export function PageCheckbox({ ids, selection }: { ids: string[]; selection: Selection }) {
  const count = ids.filter((id) => selection.isSelected(id)).length
  const all = ids.length > 0 && count === ids.length
  return (
    <Checkbox
      aria-label="Alle auf dieser Seite auswählen"
      checked={all ? true : count > 0 ? 'indeterminate' : false}
      onCheckedChange={() => selection.setAll(ids, !all)}
    />
  )
}
