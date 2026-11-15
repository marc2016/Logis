import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormError } from '@/components/FormError'
import { formatDate } from '@/core/ledger/transactions/format'
import { readAmounts, type AssignAmounts } from './statementAssign'
import { StatementDialog } from './StatementDialog'
import { unitLabel, useStatements, type Statement } from './api'

/** Commission rate and cleaning, entered once for all bookings that are assigned together. */
export function AmountFields({ rate, cleaning, onRate, onCleaning }: { rate: string; cleaning: string; onRate: (v: string) => void; onCleaning: (v: string) => void }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <div className="space-y-2">
        <Label htmlFor="bulk-rate">Provision (%)</Label>
        <Input id="bulk-rate" inputMode="decimal" placeholder="15" value={rate} onChange={(e) => onRate(e.target.value)} />
      </div>
      <div className="space-y-2">
        <Label htmlFor="bulk-cleaning">Reinigung je Buchung (€)</Label>
        <Input id="bulk-cleaning" inputMode="decimal" placeholder="0,00" value={cleaning} onChange={(e) => onCleaning(e.target.value)} />
      </div>
    </div>
  )
}

interface Props {
  /** Units of the selected bookings: the statements of these units are offered. */
  unitIds: number[]
  count: number
  onAssign: (statement: Statement, amounts: AssignAmounts) => void
  onClose: () => void
}

/** Chooses the statement and the amounts for assigning the selected bookings. */
export function AssignToStatementDialog({ unitIds, count, onAssign, onClose }: Props) {
  const { data: statements = [] } = useStatements()
  const offered = statements.filter((s) => s.canEdit && unitIds.includes(s.unit.id)).sort((a, b) => b.periodFrom.localeCompare(a.periodFrom))
  const [statementId, setStatementId] = useState('')
  const [created, setCreated] = useState<Statement | null>(null)
  const [creating, setCreating] = useState(false)
  const [rate, setRate] = useState('')
  const [cleaning, setCleaning] = useState('')
  const [error, setError] = useState<string | null>(null)
  const chosen = created && String(created.id) === statementId ? created : offered.find((s) => String(s.id) === statementId)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!chosen) {
      setError('Bitte eine Abrechnung wählen.')
      return
    }
    const amounts = readAmounts(rate, cleaning)
    if (typeof amounts === 'string') {
      setError(amounts)
      return
    }
    onAssign(chosen, amounts)
  }

  if (creating) {
    return (
      <StatementDialog
        statement={null}
        defaultUnitId={unitIds.length === 1 ? unitIds[0] : undefined}
        onSaved={(s) => {
          setCreated(s)
          setStatementId(String(s.id))
          setCreating(false)
        }}
        onClose={() => setCreating(false)}
      />
    )
  }

  const options = created && !offered.some((s) => s.id === created.id) ? [created, ...offered] : offered
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Abrechnung zuordnen</DialogTitle>
            <DialogDescription>
              {count} {count === 1 ? 'Buchung' : 'Buchungen'} mit ihrem Mietpreis als Brutto zuordnen.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="bulk-statement">Abrechnung</Label>
            <div className="flex gap-2">
              <Select value={statementId} onValueChange={setStatementId}>
                <SelectTrigger id="bulk-statement" className="w-full">
                  <SelectValue placeholder="Abrechnung wählen" />
                </SelectTrigger>
                <SelectContent>
                  {options.map((s) => (
                    <SelectItem key={s.id} value={String(s.id)}>
                      {s.provider} · {unitLabel(s.unit)} · {formatDate(s.periodFrom)} – {formatDate(s.periodTo)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button type="button" variant="outline" onClick={() => setCreating(true)}>
                Neue Abrechnung …
              </Button>
            </div>
          </div>
          <AmountFields rate={rate} cleaning={cleaning} onRate={setRate} onCleaning={setCleaning} />
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit">Zuordnen</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
