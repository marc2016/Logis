import { PlusIcon, Trash2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { EMPTY_TENANT, type Tenant } from './api'
import type { RentDraft } from './rentDraft'

/** Cold rent, advance payment for utilities and further items such as a parking space. */
export function RentFields({ idPrefix, value, onChange }: { idPrefix: string; value: RentDraft; onChange: (value: RentDraft) => void }) {
  const setOther = (index: number, changes: Partial<RentDraft['others'][number]>) =>
    onChange({ ...value, others: value.others.map((o, i) => (i === index ? { ...o, ...changes } : o)) })
  return (
    <div className="space-y-3">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-cold`}>Kaltmiete (€)</Label>
          <Input
            id={`${idPrefix}-cold`}
            inputMode="decimal"
            placeholder="0,00"
            value={value.coldRent}
            onChange={(e) => onChange({ ...value, coldRent: e.target.value })}
            required
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-utilities`}>Nebenkosten-Vorauszahlung (€)</Label>
          <Input
            id={`${idPrefix}-utilities`}
            inputMode="decimal"
            placeholder="0,00"
            value={value.utilitiesAdvance}
            onChange={(e) => onChange({ ...value, utilitiesAdvance: e.target.value })}
          />
        </div>
      </div>
      {value.others.map((other, i) => (
        <div key={i} className="grid grid-cols-[1fr_7rem_auto] items-end gap-2" role="group" aria-label={`Weiterer Posten ${i + 1}`}>
          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-other-${i}-label`}>Bezeichnung</Label>
            <Input id={`${idPrefix}-other-${i}-label`} placeholder="z. B. Stellplatz" value={other.label} maxLength={100} onChange={(e) => setOther(i, { label: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-other-${i}-amount`}>Betrag (€)</Label>
            <Input id={`${idPrefix}-other-${i}-amount`} inputMode="decimal" placeholder="0,00" value={other.amount} onChange={(e) => setOther(i, { amount: e.target.value })} />
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={`Posten ${i + 1} entfernen`}
            onClick={() => onChange({ ...value, others: value.others.filter((_, j) => j !== i) })}
          >
            <Trash2Icon />
          </Button>
        </div>
      ))}
      <Button type="button" variant="outline" size="sm" onClick={() => onChange({ ...value, others: [...value.others, { label: '', amount: '' }] })}>
        <PlusIcon />
        Weiterer Posten
      </Button>
    </div>
  )
}

const CONTACT: { key: keyof Omit<Tenant, 'id' | 'name'>; label: string; className?: string; type?: string; max: number }[] = [
  { key: 'street', label: 'Straße', className: 'sm:col-span-2', max: 200 },
  { key: 'postalCode', label: 'PLZ', max: 16 },
  { key: 'city', label: 'Ort', max: 128 },
  { key: 'phone', label: 'Telefon', max: 64 },
  { key: 'email', label: 'E-Mail', type: 'email', max: 254 },
]

/** One or more tenants with name and contact data; the last one cannot be removed. */
export function TenantFields({ idPrefix, value, onChange }: { idPrefix: string; value: Tenant[]; onChange: (value: Tenant[]) => void }) {
  const set = (index: number, changes: Partial<Tenant>) => onChange(value.map((t, i) => (i === index ? { ...t, ...changes } : t)))
  return (
    <div className="space-y-3">
      {value.map((tenant, i) => (
        <fieldset key={i} className="space-y-3 rounded-lg border p-3">
          <legend className="px-1 text-sm font-medium">Mieter {i + 1}</legend>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor={`${idPrefix}-${i}-name`}>Name</Label>
              <Input id={`${idPrefix}-${i}-name`} value={tenant.name ?? ''} maxLength={200} onChange={(e) => set(i, { name: e.target.value })} required />
            </div>
            {CONTACT.map((field) => (
              <div key={field.key} className={`space-y-2 ${field.className ?? ''}`}>
                <Label htmlFor={`${idPrefix}-${i}-${field.key}`}>{field.label}</Label>
                <Input
                  id={`${idPrefix}-${i}-${field.key}`}
                  type={field.type}
                  maxLength={field.max}
                  value={tenant[field.key] ?? ''}
                  onChange={(e) => set(i, { [field.key]: e.target.value })}
                />
              </div>
            ))}
          </div>
          {value.length > 1 && (
            <Button type="button" variant="ghost" size="sm" onClick={() => onChange(value.filter((_, j) => j !== i))}>
              <Trash2Icon />
              Mieter {i + 1} entfernen
            </Button>
          )}
        </fieldset>
      ))}
      <Button type="button" variant="outline" size="sm" onClick={() => onChange([...value, { ...EMPTY_TENANT }])}>
        <PlusIcon />
        Weiterer Mieter
      </Button>
    </div>
  )
}
