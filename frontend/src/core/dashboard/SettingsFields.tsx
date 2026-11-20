import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

const ALL = 'all'

/** A choice among fixed numbers, e.g. 7, 14 or 30 days. */
export function NumberChoice({ id, label, value, options, onChange, unit }: {
  id: string
  label: string
  value: number
  options: number[]
  onChange: (value: number) => void
  unit: string
}) {
  return (
    <div className="space-y-1">
      <Label htmlFor={id}>{label}</Label>
      <Select value={String(value)} onValueChange={(v) => onChange(Number(v))}>
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((o) => (
            <SelectItem key={o} value={String(o)}>
              {o} {unit}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

/** One object or all, e.g. one account or one unit. */
export function ObjectChoice({ id, label, value, options, onChange, allLabel }: {
  id: string
  label: string
  value: number | undefined
  options: { id: number; name: string }[]
  onChange: (value: number | undefined) => void
  allLabel: string
}) {
  return (
    <div className="space-y-1">
      <Label htmlFor={id}>{label}</Label>
      <Select value={value === undefined ? ALL : String(value)} onValueChange={(v) => onChange(v === ALL ? undefined : Number(v))}>
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>{allLabel}</SelectItem>
          {options.map((o) => (
            <SelectItem key={o.id} value={String(o.id)}>
              {o.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
