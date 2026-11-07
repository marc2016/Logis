import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Label } from '@/components/ui/label'

const ALL = 'all'

interface Props {
  id: string
  label: string
  value: number | undefined
  options: { id: number; name: string }[]
  onChange: (value: number | undefined) => void
  allLabel?: string
}

/** A select with an "all" entry, for filters. */
export function IdSelect({ id, label, value, options, onChange, allLabel = 'Alle' }: Props) {
  return (
    <div className="space-y-1">
      <Label htmlFor={id} className="text-xs text-muted-foreground">
        {label}
      </Label>
      <Select value={value === undefined ? ALL : String(value)} onValueChange={(v) => onChange(v === ALL ? undefined : Number(v))}>
        <SelectTrigger id={id} className="w-full min-w-40">
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
