import { XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Toggle } from '@/components/ui/toggle'
import { DateField } from '@/components/DateField'
import { OPEN, type InboxFilter } from './inboxFilter'

const ALL = 'all'

export interface Option {
  value: string
  label: string
}

interface Props {
  filter: InboxFilter
  results: Option[]
  kinds: Option[]
  senders: Option[]
  onChange: (changes: Partial<InboxFilter>) => void
  onReset: () => void
}

function FilterSelect({ id, label, value, options, onChange, extra }: {
  id: string
  label: string
  value: string
  options: Option[]
  onChange: (value: string) => void
  extra?: Option
}) {
  return (
    <div className="space-y-1">
      <Label htmlFor={id} className="text-xs text-muted-foreground">
        {label}
      </Label>
      <Select value={value || ALL} onValueChange={(v) => onChange(v === ALL ? '' : v)}>
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>Alle</SelectItem>
          {extra && <SelectItem value={extra.value}>{extra.label}</SelectItem>}
          {options.map((o) => (
            <SelectItem key={o.value} value={o.value}>
              {o.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

/** Filters of the inbox; the values live in the URL of the page. */
export function InboxFilters({ filter, results, kinds, senders, onChange, onReset }: Props) {
  const active = Object.values(filter).some(Boolean)
  return (
    <div className="mb-4 space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Toggle
          variant="outline"
          size="sm"
          pressed={filter.result === OPEN}
          onPressedChange={(on) => onChange({ result: on ? OPEN : '' })}
        >
          Offen
        </Toggle>
        {active && (
          <Button type="button" variant="ghost" size="sm" onClick={onReset}>
            <XIcon />
            Filter zurücksetzen
          </Button>
        )}
      </div>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(11rem,1fr))] gap-3">
        <FilterSelect
          id="flt-result"
          label="Ergebnis"
          value={filter.result}
          options={results}
          extra={{ value: OPEN, label: 'Offen (braucht Aufmerksamkeit)' }}
          onChange={(result) => onChange({ result })}
        />
        <FilterSelect id="flt-kind" label="Art" value={filter.kind} options={kinds} onChange={(kind) => onChange({ kind })} />
        <FilterSelect id="flt-sender" label="Absender" value={filter.sender} options={senders} onChange={(sender) => onChange({ sender })} />
        <div className="space-y-1">
          <Label htmlFor="flt-from" className="text-xs text-muted-foreground">
            Eingang von
          </Label>
          <DateField id="flt-from" label="Eingang von" value={filter.from} onChange={(from) => onChange({ from })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="flt-to" className="text-xs text-muted-foreground">
            Eingang bis
          </Label>
          <DateField id="flt-to" label="Eingang bis" value={filter.to} onChange={(to) => onChange({ to })} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="flt-q" className="text-xs text-muted-foreground">
            Betreff
          </Label>
          <Input id="flt-q" type="search" placeholder="Suche im Betreff" value={filter.q} onChange={(e) => onChange({ q: e.target.value })} />
        </div>
      </div>
    </div>
  )
}
