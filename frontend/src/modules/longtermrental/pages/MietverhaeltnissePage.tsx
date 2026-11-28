import { useMemo } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { createColumnHelper } from '@tanstack/react-table'
import { PlusIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DataTable } from '@/components/DataTable'
import { Money } from '@/components/Money'
import { formatEUR } from '@/lib/money'
import { PageHeader } from '@/components/PageHeader'
import { TenancyDialog } from '../TenancyDialog'
import { StateBadge } from '../StateBadge'
import { period } from '../format'
import { backTo, tenancyPath } from '../paths'
import { tenantNames, unitLabel, useLongTermUnits, useTenancies, type Tenancy, type TenancyState } from '../api'

const ALL = 'all'
const column = createColumnHelper<Tenancy>()

const columns = [
  column.accessor('unit', { header: 'Einheit', cell: (c) => unitLabel(c.getValue()), meta: { label: 'Einheit' } }),
  column.accessor((t) => tenantNames(t), {
    id: 'tenants',
    header: 'Mieter',
    cell: (c) => (c.row.original.tenantsDeleted ? <span className="text-muted-foreground">{c.getValue()}</span> : c.getValue()),
    meta: { label: 'Mieter' },
  }),
  column.accessor((t) => period(t), { id: 'period', header: 'Zeitraum', meta: { label: 'Zeitraum' } }),
  column.accessor((t) => t.currentRent?.total ?? null, {
    id: 'rent',
    header: 'Aktuelle Miete',
    cell: (c) => (c.getValue() ? <Money amount={c.getValue()!} colored={false} /> : '–'),
    meta: { label: 'Aktuelle Miete', className: 'text-right' },
  }),
  column.accessor((t) => t.arrears, {
    id: 'arrears',
    header: 'Rückstand',
    // Arrears are decimal strings from the server; "0.00" means paid up (spec rent-payments).
    cell: (c) =>
      /^0+(\.0+)?$/.test(c.getValue()) ? (
        ''
      ) : (
        <span className={c.row.original.overdue ? 'font-medium text-destructive' : undefined} data-overdue={c.row.original.overdue ? 'true' : undefined}>
          {formatEUR(c.getValue())} Rückstand
        </span>
      ),
    meta: { label: 'Rückstand', className: 'text-right' },
  }),
  column.accessor('state', { header: 'Status', cell: (c) => <StateBadge state={c.getValue()} />, meta: { label: 'Status' } }),
]

/** What the state filter offers; without a choice the list shows running and future tenancies. */
const STATE_FILTERS: { value: string; label: string; states: TenancyState[] }[] = [
  { value: 'aktiv', label: 'Laufend und künftig', states: ['RUNNING', 'FUTURE'] },
  { value: 'RUNNING', label: 'Laufend', states: ['RUNNING'] },
  { value: 'FUTURE', label: 'Künftig', states: ['FUTURE'] },
  { value: 'ENDED', label: 'Beendet', states: ['ENDED'] },
  { value: ALL, label: 'Alle', states: [] },
]

interface FilterProps {
  id: string
  label: string
  value: string
  options: { value: string; label: string }[]
  onChange: (value: string) => void
}

function FilterSelect({ id, label, value, options, onChange }: FilterProps) {
  return (
    <div className="space-y-1">
      <Label htmlFor={id} className="text-xs text-muted-foreground">
        {label}
      </Label>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger id={id} className="w-full min-w-40">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
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

/** The tenancies of the visible units with filters kept in the address (spec tenancies). */
export function MietverhaeltnissePage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { data: units = [] } = useLongTermUnits()

  const num = (key: string) => (params.get(key) ? Number(params.get(key)) : undefined)
  const stateFilter = STATE_FILTERS.find((f) => f.value === params.get('state')) ?? STATE_FILTERS[0]
  const propertyId = num('propertyId')
  const unitId = num('unitId')
  const { data: tenancies = [], isLoading } = useTenancies({ propertyId, unitId, states: stateFilter.states })
  // „neu“ opens the dialog, with a unit preselected when it comes from „Einheiten“.
  const creating = params.has('neu')
  const canRecord = units.some((u) => u.canEdit)

  const properties = useMemo(() => {
    const map = new Map<number, string>()
    units.forEach((u) => map.set(u.unit.propertyId, u.unit.propertyName))
    return [...map.entries()].map(([id, name]) => ({ value: String(id), label: name })).sort((a, b) => a.label.localeCompare(b.label, 'de'))
  }, [units])
  const unitOptions = units
    .filter((u) => propertyId === undefined || u.unit.propertyId === propertyId)
    .map((u) => ({ value: String(u.unit.id), label: unitLabel(u.unit) }))

  function update(changes: Record<string, string | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === undefined ? next.delete(k) : next.set(k, v)))
    setParams(next, { replace: true })
  }
  const choice = (value: string) => (value === ALL ? undefined : value)

  return (
    <>
      <PageHeader
        title="Mietverhältnisse"
        description="Wer welche Einheit seit wann zu welcher Miete bewohnt."
        actions={
          canRecord && (
            <Button onClick={() => update({ neu: '' })}>
              <PlusIcon />
              Mietverhältnis
            </Button>
          )
        }
      />
      <div className="mb-4 grid gap-3 @md:grid-cols-2 @3xl:grid-cols-4">
        <FilterSelect
          id="tn-filter-property"
          label="Objekt"
          value={propertyId === undefined ? ALL : String(propertyId)}
          options={[{ value: ALL, label: 'Alle' }, ...properties]}
          onChange={(v) => update({ propertyId: choice(v), unitId: undefined })}
        />
        <FilterSelect
          id="tn-filter-unit"
          label="Einheit"
          value={unitId === undefined ? ALL : String(unitId)}
          options={[{ value: ALL, label: 'Alle' }, ...unitOptions]}
          onChange={(v) => update({ unitId: choice(v) })}
        />
        <FilterSelect
          id="tn-filter-state"
          label="Status"
          value={stateFilter.value}
          options={STATE_FILTERS}
          onChange={(v) => update({ state: v === STATE_FILTERS[0].value ? undefined : v })}
        />
      </div>
      <DataTable
        tableId="longtermrental.tenancies"
        columns={columns}
        data={tenancies}
        getRowId={(t) => String(t.id)}
        onRowClick={(t) => navigate(tenancyPath(t.id), { state: backTo(location) })}
        rowLabel={(t) => `Mietverhältnis ${tenantNames(t)}, ${unitLabel(t.unit)}`}
        isLoading={isLoading}
        emptyText="Keine Mietverhältnisse."
      />
      {creating && (
        <TenancyDialog
          tenancy={null}
          defaultUnitId={num('neu')}
          onSaved={(saved) => navigate(tenancyPath(saved.id), { state: { back: location.pathname } })}
          onClose={() => update({ neu: undefined })}
        />
      )}
    </>
  )
}
