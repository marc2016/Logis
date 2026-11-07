import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Money } from '@/components/Money'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/core/ledger/transactions/format'
import { useInvalidateRental, type Booking, type Guest } from './api'

interface Props {
  booking: Booking
  onChanged: (booking: Booking | null) => void
}

type Row = { label: string; before: React.ReactNode; after: React.ReactNode }

const guestLabels: Record<keyof Guest, string> = {
  name: 'Gast',
  street: 'Straße',
  postalCode: 'PLZ',
  city: 'Ort',
  country: 'Land',
  phone: 'Telefon',
  email: 'E-Mail',
}

/** Old and new values of a proposed change; only fields the mail changes. */
function proposalRows(b: Booking): Row[] {
  const p = b.proposal
  if (!p || p.type === 'CANCELLATION') return []
  const rows: Row[] = []
  if (p.arrival) rows.push({ label: 'Anreise', before: formatDate(b.arrival), after: formatDate(p.arrival) })
  if (p.departure) rows.push({ label: 'Abreise', before: formatDate(b.departure), after: formatDate(p.departure) })
  if (p.adults != null) rows.push({ label: 'Erwachsene', before: b.adults, after: p.adults })
  if (p.children != null) rows.push({ label: 'Kinder', before: b.children, after: p.children })
  if (p.pets != null) rows.push({ label: 'Haustiere', before: b.pets, after: p.pets })
  if (p.expectedRent)
    rows.push({
      label: 'Mietpreis',
      before: b.expectedRent ? <Money amount={b.expectedRent} colored={false} /> : '–',
      after: <Money amount={p.expectedRent} colored={false} />,
    })
  if (p.guest) {
    ;(Object.keys(guestLabels) as (keyof Guest)[]).forEach((k) => {
      const value = p.guest?.[k]
      if (value) rows.push({ label: guestLabels[k], before: b.guest?.[k] ?? '–', after: value })
    })
  }
  return rows
}

/** Confirm or discard a booking taken from a mail, or apply or discard a change a mail proposes. */
export function ReviewPanel({ booking, onChanged }: Props) {
  const invalidate = useInvalidateRental()
  const base = `/api/vacationrental/bookings/${booking.id}`
  const action = useMutation({
    mutationFn: ({ url }: { url: string; done: string; closes?: boolean }) => api<Booking | undefined>(url, { method: 'POST' }),
    onSuccess: async (result, { done, closes }) => {
      await invalidate()
      toast.success(done)
      onChanged(closes ? null : (result ?? null))
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  if (booking.reviewState === 'NEW') {
    return (
      <Alert>
        <AlertTitle>Aus E-Mail übernommen – bitte prüfen</AlertTitle>
        <AlertDescription className="space-y-2">
          <p>Die Angaben stammen aus der Mail des Vermittlers. Korrigiere sie bei Bedarf und speichere, dann bestätige die Buchung.</p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" size="sm" disabled={action.isPending} onClick={() => action.mutate({ url: `${base}/confirm`, done: 'Buchung bestätigt.' })}>
              Bestätigen
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={action.isPending}
              onClick={() => action.mutate({ url: `${base}/discard`, done: 'Buchung verworfen.', closes: true })}
            >
              Verwerfen
            </Button>
          </div>
        </AlertDescription>
      </Alert>
    )
  }
  if (!booking.proposal) return null
  const cancellation = booking.proposal.type === 'CANCELLATION'
  const rows = proposalRows(booking)
  return (
    <Alert>
      <AlertTitle>{cancellation ? 'Stornierung per E-Mail – bitte prüfen' : 'Änderung per E-Mail – bitte prüfen'}</AlertTitle>
      <AlertDescription className="space-y-2">
        {cancellation ? (
          <p>Der Vermittler meldet, dass diese Buchung storniert wurde.</p>
        ) : (
          <Table aria-label="Vorgeschlagene Änderung">
            <TableHeader>
              <TableRow>
                <TableHead>Feld</TableHead>
                <TableHead>Bisher</TableHead>
                <TableHead>Neu</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.label}>
                  <TableCell>{r.label}</TableCell>
                  <TableCell className="text-muted-foreground">{r.before}</TableCell>
                  <TableCell className="font-medium">{r.after}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            disabled={action.isPending}
            onClick={() => action.mutate({ url: `${base}/proposal/apply`, done: cancellation ? 'Buchung storniert.' : 'Änderung übernommen.' })}
          >
            {cancellation ? 'Stornierung übernehmen' : 'Übernehmen'}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={action.isPending}
            onClick={() => action.mutate({ url: `${base}/proposal/discard`, done: 'Vorschlag verworfen.' })}
          >
            Verwerfen
          </Button>
        </div>
      </AlertDescription>
    </Alert>
  )
}
