import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeftIcon, PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ConfirmButton } from '@/components/ConfirmButton'
import { Money } from '@/components/Money'
import { ApiError, api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/core/ledger/transactions/format'
import { StatementDialog } from '../StatementDialog'
import { StatementLineDialog } from '../StatementLineDialog'
import { backLabel, backTo, bookingPath, type BackState } from '../BookingPage.paths'
import { statusLabel, unitLabel, useInvalidateRental, useStatement, type StatementLine } from '../api'

const LIST = '/ferienvermietung/abrechnungen'

type DialogState = { kind: 'statement' } | { kind: 'line'; line: StatementLine | null } | null

/** A statement as its own page: header with the key figures, its booking lines and the payout. */
export function StatementPage() {
  const { statementId } = useParams()
  const id = Number(statementId)
  const location = useLocation()
  const navigate = useNavigate()
  const back = (location.state as BackState | null)?.back ?? LIST
  const { data: selected, error, isLoading } = useStatement(Number.isFinite(id) ? id : null)
  const [dialog, setDialog] = useState<DialogState>(null)
  const invalidate = useInvalidateRental()

  const remove = useMutation({
    mutationFn: ({ url }: { url: string; done: string; leave?: boolean }) => api(url, { method: 'DELETE' }),
    onSuccess: async (_, { done, leave }) => {
      await invalidate()
      toast.success(done)
      if (leave) navigate(back)
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const backLink = (
    <Link to={back} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ArrowLeftIcon className="size-4" /> {backLabel(back)}
    </Link>
  )

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96" />
      </div>
    )
  }
  if (!selected) {
    const notFound = !Number.isFinite(id) || (error instanceof ApiError && error.status === 404)
    return (
      <div className="space-y-4">
        {backLink}
        <p role="alert">{notFound ? 'Abrechnung nicht gefunden' : errorMessage(error)}</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {backLink}
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold tracking-tight">{selected.provider}</h1>
          <dl className="flex flex-wrap gap-x-6 gap-y-1 text-sm" aria-label="Wichtige Angaben">
            <div>
              <dt className="sr-only">Einheit</dt>
              <dd>{unitLabel(selected.unit)}</dd>
            </div>
            <div>
              <dt className="sr-only">Zeitraum</dt>
              <dd className="font-medium">
                {formatDate(selected.periodFrom)} – {formatDate(selected.periodTo)}
              </dd>
            </div>
            <div>
              <dt className="sr-only">Abrechnungsdatum</dt>
              <dd className="text-muted-foreground">vom {formatDate(selected.statementDate)}</dd>
            </div>
            <div>
              <dt className="sr-only">Auszahlung</dt>
              <dd className="font-medium">
                Auszahlung <Money amount={selected.payout} />
              </dd>
            </div>
          </dl>
        </div>
        {selected.canEdit && (
          <div className="flex flex-wrap gap-2">
            <Button size="sm" onClick={() => setDialog({ kind: 'line', line: null })}>
              <PlusIcon />
              Buchung zuordnen
            </Button>
            <Button size="sm" variant="outline" onClick={() => setDialog({ kind: 'statement' })}>
              <PencilIcon />
              Bearbeiten
            </Button>
            <ConfirmButton
              question="Abrechnung löschen? Die Buchungen werden wieder geplant."
              disabled={remove.isPending}
              onConfirm={() => remove.mutate({ url: `/api/vacationrental/statements/${selected.id}`, done: 'Abrechnung gelöscht.', leave: true })}
            >
              <Trash2Icon />
              Löschen
            </ConfirmButton>
          </div>
        )}
      </header>
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Aufenthalt</TableHead>
              <TableHead>Gast</TableHead>
              <TableHead className="text-right">Brutto</TableHead>
              <TableHead className="text-right">Provision</TableHead>
              <TableHead className="text-right">Reinigung</TableHead>
              <TableHead className="text-right">Netto</TableHead>
              {selected.canEdit && <TableHead className="w-24" />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {selected.lines.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="py-6 text-center text-muted-foreground">
                  Noch keine Buchung zugeordnet.
                </TableCell>
              </TableRow>
            ) : (
              selected.lines.map((l) => (
                <TableRow key={l.booking.id}>
                  <TableCell>
                    <Link to={bookingPath(l.booking.id)} state={backTo(location)} className="hover:underline">
                      {formatDate(l.booking.arrival)} – {formatDate(l.booking.departure)}
                    </Link>
                    {l.booking.externalRef && <span className="text-muted-foreground"> · {l.booking.externalRef}</span>}
                    {l.booking.status === 'CANCELLED' && (
                      <Badge variant="secondary" className="ml-2">
                        {statusLabel.CANCELLED}
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell>{l.booking.guest?.name ?? '–'}</TableCell>
                  <TableCell className="text-right">
                    <Money amount={l.gross} colored={false} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Money amount={l.commission} colored={false} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Money amount={l.cleaning} colored={false} />
                  </TableCell>
                  <TableCell className="text-right font-medium">
                    <Money amount={l.net} />
                  </TableCell>
                  {selected.canEdit && (
                    <TableCell className="text-right">
                      <Button variant="ghost" size="icon" aria-label="Position bearbeiten" onClick={() => setDialog({ kind: 'line', line: l })}>
                        <PencilIcon />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label="Position entfernen"
                        disabled={remove.isPending}
                        onClick={() =>
                          remove.mutate({ url: `/api/vacationrental/statements/${selected.id}/lines/${l.booking.id}`, done: 'Position entfernt.' })
                        }
                      >
                        <Trash2Icon />
                      </Button>
                    </TableCell>
                  )}
                </TableRow>
              ))
            )}
          </TableBody>
          <TableFooter>
            <TableRow>
              <TableCell colSpan={5}>Auszahlung</TableCell>
              <TableCell className="text-right font-semibold" data-testid="payout">
                <Money amount={selected.payout} />
              </TableCell>
              {selected.canEdit && <TableCell />}
            </TableRow>
          </TableFooter>
        </Table>
      </div>

      {dialog?.kind === 'statement' && <StatementDialog statement={selected} onSaved={() => setDialog(null)} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'line' && <StatementLineDialog statement={selected} line={dialog.line} onClose={() => setDialog(null)} />}
    </div>
  )
}
