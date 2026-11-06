import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select'
import { atLeast, NO_TARGET, useProperties, type Property, type Target } from '@/core/realestate/api'

const NONE = 'none'

function encode(t: Target) {
  return t.unitId != null ? `u:${t.unitId}` : t.propertyId != null ? `p:${t.propertyId}` : NONE
}

function decode(value: string): Target {
  const [kind, id] = value.split(':')
  if (kind === 'u') return { propertyId: null, unitId: Number(id) }
  if (kind === 'p') return { propertyId: Number(id), unitId: null }
  return NO_TARGET
}

interface Option {
  value: string
  label: string
}

/**
 * Options per property. For attributing ("assign") only active targets with EDITOR rights are offered; for
 * filtering everything visible. Single-unit properties appear as one entry that stands for their unit.
 */
function options(property: Property, mode: 'assign' | 'filter'): Option[] {
  const usable = (role: Parameters<typeof atLeast>[0], archived: boolean) =>
    mode === 'filter' || (atLeast(role, 'EDITOR') && !archived && !property.archived)
  if (!property.multiUnit && property.units.length === 1) {
    const unit = property.units[0]
    return usable(unit.myRole, unit.archived) ? [{ value: `u:${unit.id}`, label: property.name }] : []
  }
  const result: Option[] = []
  if (property.myRole && usable(property.myRole, false)) {
    result.push({ value: `p:${property.id}`, label: mode === 'filter' ? `${property.name} (alle)` : `${property.name} (ganzes Objekt)` })
  }
  for (const unit of property.units) {
    if (usable(unit.myRole, unit.archived)) result.push({ value: `u:${unit.id}`, label: unit.name })
  }
  return result
}

interface Props {
  id: string
  value: Target
  onChange: (t: Target) => void
  mode: 'assign' | 'filter'
  /** Label of the current target if it is no longer selectable (e.g. archived). */
  currentName?: string | null
}

/** Picks a property or unit as target of a transaction, or as filter of the transaction list. */
export function TargetSelect({ id, value, onChange, mode, currentName }: Props) {
  const { data: properties = [] } = useProperties(mode === 'filter')
  const groups = properties.map((p) => ({ property: p, options: options(p, mode) })).filter((g) => g.options.length > 0)
  const current = encode(value)
  const known = current === NONE || groups.some((g) => g.options.some((o) => o.value === current))

  return (
    <Select value={current} onValueChange={(v) => onChange(decode(v))}>
      <SelectTrigger id={id} className="w-full">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={NONE}>{mode === 'filter' ? 'Alle Objekte' : 'Kein Objekt'}</SelectItem>
        {!known && <SelectItem value={current}>{currentName ?? 'Bisheriges Objekt'}</SelectItem>}
        {groups.map(({ property, options }) =>
          property.multiUnit ? (
            <SelectGroup key={property.id}>
              <SelectLabel>{property.name}</SelectLabel>
              {options.map((o) => (
                <SelectItem key={o.value} value={o.value}>
                  {o.label}
                </SelectItem>
              ))}
            </SelectGroup>
          ) : (
            options.map((o) => (
              <SelectItem key={o.value} value={o.value}>
                {o.label}
              </SelectItem>
            ))
          ),
        )}
      </SelectContent>
    </Select>
  )
}
