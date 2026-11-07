import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { parseAmount, toInputValue } from '@/lib/money'
import { formatDate } from '@/core/ledger/transactions/format'
import { statusLabel, useBookings, useInvalidateRental, type Statement, type StatementLine } from './api'

interface Props {
  statement: Statement
  /** Existing line to change, or null to assign a booking. */
  line: StatementLine | null
  onClose: () => void
}

/** Assigns a booking of the statement's unit with gross, commission and cleaning, or changes those amounts. */
export function StatementLineDialog({ statement, line, onClose }: Props) {
  const { data } = useBookings({ unitId: statement.unit.id, sort: 'arrival,asc', size: 100 })
  const open = (data?.items ?? []).filter((b) => b.status !== 'SETTLED')
  const [bookingId, setBookingId] = useState(line ? String(line.booking.id) : '')
  const chosen = line?.booking ?? open.find((b) => String(b.id) === bookingId)
  const [gross, setGross] = useState(line ? toInputValue(line.gross) : '')
  const [commission, setCommission] = useState(line ? toInputValue(line.commission) : '')
  const [cleaning, setCleaning] = useState(line ? toInputValue(line.cleaning) : '')
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateRental()

  function pick(id: string) {
    setBookingId(id)
    const booking = open.find((b) => String(b.id) === id)
    if (!gross && booking?.expectedRent) setGross(toInputValue(booking.expectedRent))
  }

  const save = useMutation({
    mutationFn: (body: { gross: string | null; commission: string | null; cleaning: string | null }) =>
      api<Statement>(`/api/vacationrental/statements/${statement.id}/lines/${bookingId}`, { method: 'PUT', json: body }),
    onSuccess: async () => {
      await invalidate()
      toast.success(line ? 'Position gespeichert.' : 'Buchung zugeordnet.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function amount(text: string, label: string): string | null | false {
    if (!text.trim()) return null
    const parsed = parseAmount(text)
    if (parsed === null || parsed.startsWith('-')) {
      setError(`Bitte einen gültigen Betrag für ${label} eingeben, z. B. 37,29.`)
      return false
    }
    return parsed
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const g = amount(gross, 'Brutto')
    const c = amount(commission, 'Provision')
    const r = amount(cleaning, 'Reinigung')
    if (g === false || c === false || r === false) return
    save.mutate({ gross: g, commission: c, cleaning: r })
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{line ? 'Position bearbeiten' : 'Buchung zuordnen'}</DialogTitle>
            <DialogDescription>Netto = Brutto − Provision − Reinigung. Die Auszahlung ist die Summe der Netto-Beträge.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="ln-booking">Buchung</Label>
            <Select value={bookingId} onValueChange={pick} disabled={line !== null}>
              <SelectTrigger id="ln-booking" className="w-full">
                <SelectValue placeholder={open.length || line ? 'Buchung wählen' : 'Keine offene Buchung'} />
              </SelectTrigger>
              <SelectContent>
                {(line ? [line.booking] : open).map((b) => (
                  <SelectItem key={b.id} value={String(b.id)}>
                    {formatDate(b.arrival)} – {formatDate(b.departure)}
                    {b.externalRef ? ` · ${b.externalRef}` : ''}
                    {b.guest?.name ? ` · ${b.guest.name}` : ''}
                    {b.status === 'CANCELLED' ? ` (${statusLabel.CANCELLED})` : ''}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-2">
              <Label htmlFor="ln-gross">Brutto (€)</Label>
              <Input
                id="ln-gross"
                inputMode="decimal"
                placeholder={chosen?.expectedRent ? toInputValue(chosen.expectedRent) : '0,00'}
                value={gross}
                onChange={(e) => setGross(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="ln-commission">Provision (€)</Label>
              <Input id="ln-commission" inputMode="decimal" placeholder="0,00" value={commission} onChange={(e) => setCommission(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="ln-cleaning">Reinigung (€)</Label>
              <Input id="ln-cleaning" inputMode="decimal" placeholder="0,00" value={cleaning} onChange={(e) => setCleaning(e.target.value)} />
            </div>
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={save.isPending || !bookingId}>
              Speichern
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
