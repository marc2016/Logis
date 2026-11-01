import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useCategories, type Category, type CategoryKind } from '@/core/ledger/api'

const NONE = 'none'
const kindLabel: Record<CategoryKind, string> = { INCOME: 'Einnahmen', EXPENSE: 'Ausgaben' }

interface Props {
  id?: string
  value: number | null
  onChange: (value: number | null) => void
  /** Keep showing a category that is already assigned but has since been archived. */
  currentName?: string | null
  placeholder?: string
  noneLabel?: string
}

/** Selection from the shared category tree, grouped by income/expenses. */
export function CategorySelect({ id, value, onChange, currentName, placeholder = 'Kategorie wählen', noneLabel = 'Keine Kategorie' }: Props) {
  const { data: tree = [] } = useCategories()
  const known = new Set<number>()
  tree.forEach((c) => {
    known.add(c.id)
    c.children.forEach((child) => known.add(child.id))
  })
  const archivedCurrent = value !== null && !known.has(value) && currentName

  return (
    <Select value={value === null ? NONE : String(value)} onValueChange={(v) => onChange(v === NONE ? null : Number(v))}>
      <SelectTrigger id={id} className="w-full">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={NONE}>{noneLabel}</SelectItem>
        {archivedCurrent && <SelectItem value={String(value)}>{currentName} (archiviert)</SelectItem>}
        {(['EXPENSE', 'INCOME'] as const).map((kind) => (
          <SelectGroup key={kind}>
            <SelectLabel>{kindLabel[kind]}</SelectLabel>
            {tree
              .filter((c: Category) => c.kind === kind)
              .flatMap((c) => [
                <SelectItem key={c.id} value={String(c.id)}>
                  {c.name}
                </SelectItem>,
                ...c.children.map((child) => (
                  <SelectItem key={child.id} value={String(child.id)} className="pl-6">
                    {child.name}
                  </SelectItem>
                )),
              ])}
          </SelectGroup>
        ))}
      </SelectContent>
    </Select>
  )
}
