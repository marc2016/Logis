import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatEUR } from '@/lib/money'
import { formatDate } from '@/core/ledger/transactions/format'
import { statusLabel, useBookings, useInvalidateRental, type Statement, type StatementOpenLine } from './api'

interface Props {
  statement: Statement
  line: StatementOpenLine
  onClose: () => void
}

/**
 * „Zuordnen“ of an open line (spec rental-statements, „Open lines“): bookings of the unit on no statement or on this
 * one; owner uses and bookings on other statements are not offered. A booking with the line's period is preselected.
 */
export function OpenLineDialog({ statement, line, onClose }: Props) {
  const { data } = useBookings({ unitId: statement.unit.id, kind: 'GUEST', sort: 'arrival,asc', size: 100 })
  const offered = (data?.items ?? []).filter((b) => b.statementId === null || b.statementId === statement.id)
  const samePeriod = offered.find((b) => b.arrival === line.arrival && b.departure === line.departure)
  const [chosen, setChosen] = useState('')
  const bookingId = chosen || (samePeriod ? String(samePeriod.id) : '')
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateRental()

  const assign = useMutation({
    mutationFn: () =>
      api<Statement>(`/api/vacationrental/statements/${statement.id}/open-lines/${line.id}/assign`, {
        method: 'POST',
        json: { bookingId: Number(bookingId) },
      }),
    onSuccess: async () => {
      await invalidate()
      toast.success('Zeile zugeordnet.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    assign.mutate()
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Zeile zuordnen</DialogTitle>
            <DialogDescription>
              {line.guestName}, {formatDate(line.arrival)} – {formatDate(line.departure)}: Brutto {formatEUR(line.gross)}, Provision{' '}
              {formatEUR(line.commission)}. Ist die Buchung schon in dieser Abrechnung, werden die Beträge addiert.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="ol-booking">Buchung</Label>
            <Select value={bookingId} onValueChange={setChosen}>
              <SelectTrigger id="ol-booking" className="w-full">
                <SelectValue placeholder={offered.length ? 'Buchung wählen' : 'Keine passende Buchung'} />
              </SelectTrigger>
              <SelectContent>
                {offered.map((b) => (
                  <SelectItem key={b.id} value={String(b.id)}>
                    {formatDate(b.arrival)} – {formatDate(b.departure)}
                    {b.guest?.name ? ` · ${b.guest.name}` : ''}
                    {b.statementId === statement.id ? ' (in dieser Abrechnung)' : ''}
                    {b.status === 'CANCELLED' ? ` (${statusLabel.CANCELLED})` : ''}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={assign.isPending || !bookingId}>
              Zuordnen
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
