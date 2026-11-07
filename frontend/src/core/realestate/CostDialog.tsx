import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Textarea } from '@/components/ui/textarea'
import { CategorySelect } from '@/components/CategorySelect'
import { ConfirmButton } from '@/components/ConfirmButton'
import { DocumentList, PendingDocuments } from '@/components/DocumentList'
import { uploadDocument } from '@/components/documents'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { parseAmount, toInputValue } from '@/lib/money'
import { todayIso } from '@/core/ledger/transactions/format'
import { atLeast, COST_SOURCE, useInvalidateFinances, type Cost, type Property } from './api'

const PROPERTY_LEVEL = 'property'

interface Props {
  property: Property
  /** Existing cost, or null for a new one. */
  cost: Cost | null
  onClose: () => void
}

/** Records a cost directly on the property or one of its units, without an account; receipts attach to it. */
export function CostDialog({ property, cost: initial, onClose }: Props) {
  const [cost, setCost] = useState<Cost | null>(initial)
  const isNew = cost === null
  const canEditProperty = atLeast(property.myRole, 'EDITOR')
  const editableUnits = property.units.filter((u) => atLeast(u.myRole, 'EDITOR'))
  const showTarget = property.multiUnit || !canEditProperty
  // Single-unit properties hide the unit level: costs go on the property itself.
  const defaultTarget = canEditProperty ? PROPERTY_LEVEL : String(editableUnits[0]?.id ?? '')
  const [target, setTarget] = useState(initial ? (initial.unitId === null ? PROPERTY_LEVEL : String(initial.unitId)) : defaultTarget)
  const [date, setDate] = useState(initial?.date ?? todayIso())
  const [amount, setAmount] = useState(initial ? toInputValue(initial.amount) : '')
  const [categoryId, setCategoryId] = useState<number | null>(initial?.categoryId ?? null)
  const [counterparty, setCounterparty] = useState(initial?.counterparty ?? '')
  const [note, setNote] = useState(initial?.note ?? '')
  const [pending, setPending] = useState<File[]>([])
  const [error, setError] = useState<string | null>(null)
  const [uploadErrors, setUploadErrors] = useState<string[]>([])
  const invalidate = useInvalidateFinances(property.id)

  const save = useMutation({
    mutationFn: async (parsed: string) => {
      const body = {
        unitId: target === PROPERTY_LEVEL ? null : Number(target),
        date,
        amount: parsed,
        categoryId,
        counterparty,
        note,
        version: cost?.version,
      }
      const saved = isNew
        ? await api<Cost>(`/api/properties/${property.id}/costs`, { json: body })
        : await api<Cost>(`/api/costs/${cost.id}`, { method: 'PUT', json: body })
      const failed: string[] = []
      for (const file of pending) {
        try {
          await uploadDocument(COST_SOURCE, saved.id, file)
        } catch (err) {
          failed.push(`${file.name}: ${errorMessage(err)}`)
        }
      }
      return { saved, failed }
    },
    onSuccess: async ({ saved, failed }) => {
      await invalidate()
      setPending([])
      if (failed.length) {
        setCost(saved)
        setUploadErrors(failed)
        toast.error('Die Kosten wurden gespeichert, aber nicht alle Belege.')
        return
      }
      toast.success(isNew ? 'Kosten erfasst.' : 'Kosten gespeichert.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  const remove = useMutation({
    mutationFn: () => api(`/api/costs/${cost!.id}`, { method: 'DELETE' }),
    onSuccess: async () => {
      await invalidate()
      toast.success('Kosten gelöscht.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const parsed = parseAmount(amount)
    if (parsed === null || parsed.startsWith('-') || /^0+\.00$/.test(parsed)) {
      setError('Bitte einen gültigen Betrag größer 0 eingeben, z. B. 320,00.')
      return
    }
    save.mutate(parsed)
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Kosten erfassen' : 'Kosten bearbeiten'}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="cost-amount">Betrag (€)</Label>
              <Input id="cost-amount" inputMode="decimal" placeholder="0,00" value={amount} onChange={(e) => setAmount(e.target.value)} required autoFocus />
            </div>
            <div className="space-y-2">
              <Label htmlFor="cost-date">Datum</Label>
              <Input id="cost-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
            </div>
          </div>
          {showTarget && (
            <div className="space-y-2">
              <Label htmlFor="cost-target">Einheit</Label>
              <Select value={target} onValueChange={setTarget}>
                <SelectTrigger id="cost-target" className="w-full">
                  <SelectValue placeholder="Einheit wählen" />
                </SelectTrigger>
                <SelectContent>
                  {canEditProperty && <SelectItem value={PROPERTY_LEVEL}>Ganzes Objekt</SelectItem>}
                  {editableUnits.map((u) => (
                    <SelectItem key={u.id} value={String(u.id)}>
                      {u.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="cost-category">Kategorie</Label>
            <CategorySelect id="cost-category" value={categoryId} onChange={setCategoryId} kinds={['EXPENSE']} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="cost-counterparty">Empfänger</Label>
            <Input id="cost-counterparty" value={counterparty} onChange={(e) => setCounterparty(e.target.value)} placeholder="z. B. Handwerker Meyer" />
          </div>
          <div className="space-y-2">
            <Label htmlFor="cost-note">Notiz</Label>
            <Textarea id="cost-note" value={note} onChange={(e) => setNote(e.target.value)} rows={2} />
          </div>
          <Separator />
          <section className="space-y-2" aria-label="Belege">
            <h3 className="text-sm font-medium">Belege</h3>
            {cost ? <DocumentList ownerType={COST_SOURCE} ownerId={cost.id} canEdit /> : <PendingDocuments files={pending} onChange={setPending} />}
            {uploadErrors.length > 0 && (
              <ul className="text-sm text-destructive" role="alert">
                {uploadErrors.map((e) => (
                  <li key={e}>{e}</li>
                ))}
              </ul>
            )}
          </section>
          {cost && (
            <ConfirmButton question="Kosten mit allen Belegen löschen?" disabled={remove.isPending} onConfirm={() => remove.mutate()}>
              Löschen
            </ConfirmButton>
          )}
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={save.isPending || !target}>
              Speichern
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
