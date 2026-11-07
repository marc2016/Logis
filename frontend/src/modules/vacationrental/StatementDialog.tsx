import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { todayIso } from '@/core/ledger/transactions/format'
import { unitLabel, useInvalidateRental, useProviders, useRentalUnits, type Statement } from './api'

interface Props {
  statement: Statement | null
  defaultUnitId?: number
  onSaved: (statement: Statement) => void
  onClose: () => void
}

function monthBounds() {
  const today = todayIso()
  const [y, m] = today.split('-').map(Number)
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate()
  return [`${today.slice(0, 7)}-01`, `${today.slice(0, 7)}-${String(last).padStart(2, '0')}`]
}

/** Create or edit the data of a statement; the provider is free text with suggestions from the unit. */
export function StatementDialog({ statement, defaultUnitId, onSaved, onClose }: Props) {
  const isNew = statement === null
  const { data: units = [] } = useRentalUnits()
  const editable = units.filter((u) => !u.archived && u.myRole !== 'VIEWER')
  const [from, to] = monthBounds()
  const [unitId, setUnitId] = useState(String(statement?.unit.id ?? defaultUnitId ?? editable[0]?.id ?? ''))
  const [provider, setProvider] = useState(statement?.provider ?? '')
  const [periodFrom, setPeriodFrom] = useState(statement?.periodFrom ?? from)
  const [periodTo, setPeriodTo] = useState(statement?.periodTo ?? to)
  const [statementDate, setStatementDate] = useState(statement?.statementDate ?? todayIso())
  const [error, setError] = useState<string | null>(null)
  const { data: providers = [] } = useProviders(unitId ? Number(unitId) : null)
  const invalidate = useInvalidateRental()

  if (!unitId && editable[0]) setUnitId(String(editable[0].id))
  // Suggest the most recent provider of the unit for a new statement.
  if (isNew && !provider && providers[0]) setProvider(providers[0])

  const save = useMutation({
    mutationFn: () => {
      const body = { unitId: Number(unitId), provider, periodFrom, periodTo, statementDate, version: statement?.version }
      return isNew
        ? api<Statement>('/api/vacationrental/statements', { json: body })
        : api<Statement>(`/api/vacationrental/statements/${statement.id}`, { method: 'PUT', json: body })
    },
    onSuccess: async (saved) => {
      await invalidate()
      toast.success(isNew ? 'Abrechnung angelegt.' : 'Abrechnung gespeichert.')
      onSaved(saved)
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    save.mutate()
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Abrechnung anlegen' : 'Abrechnung bearbeiten'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="st-unit">Einheit</Label>
            <Select value={unitId} onValueChange={setUnitId} disabled={!isNew}>
              <SelectTrigger id="st-unit" className="w-full">
                <SelectValue placeholder="Einheit wählen" />
              </SelectTrigger>
              <SelectContent>
                {(isNew ? editable : units).map((u) => (
                  <SelectItem key={u.id} value={String(u.id)}>
                    {unitLabel(u)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="st-provider">Dienstleister</Label>
            <Input id="st-provider" list="st-provider-suggestions" value={provider} onChange={(e) => setProvider(e.target.value)} required />
            <datalist id="st-provider-suggestions">
              {providers.map((p) => (
                <option key={p} value={p} />
              ))}
            </datalist>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-2">
              <Label htmlFor="st-from">Zeitraum von</Label>
              <Input id="st-from" type="date" value={periodFrom} onChange={(e) => setPeriodFrom(e.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="st-to">bis</Label>
              <Input id="st-to" type="date" value={periodTo} onChange={(e) => setPeriodTo(e.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="st-date">Abrechnungsdatum</Label>
              <Input id="st-date" type="date" value={statementDate} onChange={(e) => setStatementDate(e.target.value)} required />
            </div>
          </div>
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
