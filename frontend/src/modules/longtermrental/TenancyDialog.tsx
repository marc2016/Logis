import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DateField } from '@/components/DateField'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { parseAmount, toInputValue } from '@/lib/money'
import { todayIso } from '@/core/ledger/transactions/format'
import { EMPTY_TENANT, TENANCIES, unitLabel, useInvalidateTenancies, useLongTermUnits, type Tenancy, type Tenant } from './api'
import { RentFields, TenantFields } from './forms'
import { parseRent, rentDraft } from './rentDraft'

interface Props {
  /** The tenancy to change, or null for a new one. */
  tenancy: Tenancy | null
  defaultUnitId?: number
  onSaved: (tenancy: Tenancy) => void
  onClose: () => void
}

/** Record a tenancy with its tenants and first rent, or change its dates, deposit and tenants. */
export function TenancyDialog({ tenancy, defaultUnitId, onSaved, onClose }: Props) {
  const isNew = tenancy === null
  const { data: units = [] } = useLongTermUnits()
  const editable = units.filter((u) => u.canEdit).map((u) => u.unit)
  // A unit that is no long-term rental any more keeps its tenancies (design D3).
  const options = tenancy && !editable.some((u) => u.id === tenancy.unit.id) ? [tenancy.unit, ...editable] : editable
  const [unitId, setUnitId] = useState(String(tenancy?.unit.id ?? defaultUnitId ?? ''))
  const [startDate, setStartDate] = useState(tenancy?.startDate ?? todayIso())
  const [endDate, setEndDate] = useState(tenancy?.endDate ?? '')
  const [tenants, setTenants] = useState<Tenant[]>(tenancy?.tenants.length ? tenancy.tenants : [{ ...EMPTY_TENANT }])
  const [rent, setRent] = useState(rentDraft())
  const [deposit, setDeposit] = useState(tenancy?.depositAmount ? toInputValue(tenancy.depositAmount) : '')
  const [depositReceivedOn, setDepositReceivedOn] = useState(tenancy?.depositReceivedOn ?? '')
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateTenancies()

  if (!unitId && options.length === 1) setUnitId(String(options[0].id))

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      isNew ? api<Tenancy>(TENANCIES, { json: body }) : api<Tenancy>(`${TENANCIES}/${tenancy.id}`, { method: 'PUT', json: body }),
    onSuccess: async (saved) => {
      await invalidate()
      toast.success(isNew ? 'Mietverhältnis angelegt.' : 'Mietverhältnis gespeichert.')
      onSaved(saved)
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    let depositAmount: string | null = null
    if (deposit.trim()) {
      depositAmount = parseAmount(deposit)
      if (depositAmount === null || depositAmount.startsWith('-')) {
        setError('Bitte eine gültige Kaution eingeben, z. B. 1.950,00.')
        return
      }
    }
    const body: Record<string, unknown> = {
      unitId: Number(unitId),
      startDate,
      endDate: endDate || null,
      depositAmount,
      depositReceivedOn: depositAmount && depositReceivedOn ? depositReceivedOn : null,
      tenants,
      version: tenancy?.version,
    }
    if (isNew) {
      const parsed = parseRent(rent)
      if (typeof parsed === 'string') {
        setError(parsed)
        return
      }
      body.rent = parsed
    }
    save.mutate(body)
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-2xl">
        <form onSubmit={submit} className="space-y-5">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Mietverhältnis anlegen' : 'Mietverhältnis bearbeiten'}</DialogTitle>
            <DialogDescription>
              {isNew ? 'Einheit, Zeitraum, Mieter und die erste Miete.' : 'Zeitraum, Kaution und Mieter. Die Miete wird im Mietverlauf geändert.'}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="tn-unit">Einheit</Label>
            <Select value={unitId} onValueChange={setUnitId}>
              <SelectTrigger id="tn-unit" className="w-full">
                <SelectValue placeholder="Einheit wählen" />
              </SelectTrigger>
              <SelectContent>
                {options.map((u) => (
                  <SelectItem key={u.id} value={String(u.id)}>
                    {unitLabel(u)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="tn-start">Beginn</Label>
              <DateField id="tn-start" label="Beginn" value={startDate} onChange={setStartDate} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="tn-end">Ende (letzter Tag)</Label>
              <DateField id="tn-end" label="Ende (letzter Tag)" value={endDate} onChange={setEndDate} />
            </div>
          </div>
          {!tenancy?.tenantsDeleted && (
            <section className="space-y-2" aria-label="Mieter">
              <h3 className="text-sm font-medium">Mieter</h3>
              <TenantFields idPrefix="tn-tenant" value={tenants} onChange={setTenants} />
            </section>
          )}
          {isNew && (
            <section className="space-y-2" aria-label="Miete">
              <h3 className="text-sm font-medium">Miete ab Beginn</h3>
              <RentFields idPrefix="tn-rent" value={rent} onChange={setRent} />
            </section>
          )}
          <section className="space-y-2" aria-label="Kaution">
            <h3 className="text-sm font-medium">Kaution</h3>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="tn-deposit">Kaution (€)</Label>
                <Input id="tn-deposit" inputMode="decimal" placeholder="0,00" value={deposit} onChange={(e) => setDeposit(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="tn-deposit-received">Kaution erhalten am</Label>
                <DateField id="tn-deposit-received" label="Kaution erhalten am" value={depositReceivedOn} onChange={setDepositReceivedOn} />
              </div>
            </div>
          </section>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={save.isPending || !unitId}>
              Speichern
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
