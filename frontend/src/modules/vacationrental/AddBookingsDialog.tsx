import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FormError } from '@/components/FormError'
import { Money } from '@/components/Money'
import { formatDate } from '@/core/ledger/transactions/format'
import { AmountFields } from './AssignToStatementDialog'
import { readAmounts, type AssignAmounts } from './statementAssign'
import { statusLabel, useBookings, type Booking, type Statement } from './api'

interface Props {
  statement: Statement
  onAssign: (bookings: Booking[], amounts: AssignAmounts) => void
  onClose: () => void
}

/** Bookings of the statement's unit that are on no statement; those in the statement's period are preselected. */
export function AddBookingsDialog({ statement, onAssign, onClose }: Props) {
  const { data, isLoading } = useBookings({ unitId: statement.unit.id, kind: 'GUEST', sort: 'arrival,asc', size: 100 })
  const open = (data?.items ?? []).filter((b) => b.status !== 'SETTLED' && b.canEdit)
  const inPeriod = (b: Booking) => b.status !== 'CANCELLED' && b.departure >= statement.periodFrom && b.departure <= statement.periodTo
  const [chosen, setChosen] = useState<Set<number> | null>(null)
  const ids = chosen ?? new Set(open.filter(inPeriod).map((b) => b.id))
  const [rate, setRate] = useState('')
  const [cleaning, setCleaning] = useState('')
  const [error, setError] = useState<string | null>(null)

  function toggle(id: number) {
    const next = new Set(ids)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setChosen(next)
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    const bookings = open.filter((b) => ids.has(b.id))
    if (bookings.length === 0) {
      setError('Bitte mindestens eine Buchung wählen.')
      return
    }
    const amounts = readAmounts(rate, cleaning)
    if (typeof amounts === 'string') {
      setError(amounts)
      return
    }
    onAssign(bookings, amounts)
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Buchungen zuordnen</DialogTitle>
            <DialogDescription>
              Buchungen ohne Abrechnung; die mit Abreise vom {formatDate(statement.periodFrom)} bis {formatDate(statement.periodTo)} sind vorausgewählt.
            </DialogDescription>
          </DialogHeader>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Wird geladen …</p>
          ) : open.length === 0 ? (
            <p className="text-sm text-muted-foreground">Keine offenen Buchungen.</p>
          ) : (
            <ul className="max-h-72 divide-y overflow-auto rounded-md border text-sm" aria-label="Offene Buchungen">
              {open.map((b) => (
                <li key={b.id}>
                  <label className="flex items-center gap-3 px-3 py-2">
                    <Checkbox checked={ids.has(b.id)} onCheckedChange={() => toggle(b.id)} />
                    <span className="flex-1">
                      {formatDate(b.arrival)} – {formatDate(b.departure)} · {b.guest?.name ?? '–'}
                      {b.externalRef && <span className="text-muted-foreground"> · {b.externalRef}</span>}
                      {b.status === 'CANCELLED' && <span className="text-muted-foreground"> · {statusLabel.CANCELLED}</span>}
                    </span>
                    {b.expectedRent ? <Money amount={b.expectedRent} colored={false} /> : <span className="text-muted-foreground">ohne Mietpreis</span>}
                  </label>
                </li>
              ))}
            </ul>
          )}
          <AmountFields rate={rate} cleaning={cleaning} onRate={setRate} onCleaning={setCleaning} />
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit">{ids.size} zuordnen</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
