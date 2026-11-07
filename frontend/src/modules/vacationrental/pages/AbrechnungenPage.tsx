import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ConfirmButton } from '@/components/ConfirmButton'
import { Money } from '@/components/Money'
import { PageHeader } from '@/components/PageHeader'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/core/ledger/transactions/format'
import { IdSelect } from '../RentalFilters'
import { StatementDialog } from '../StatementDialog'
import { StatementLineDialog } from '../StatementLineDialog'
import { statusLabel, unitLabel, useInvalidateRental, useRentalUnits, useStatement, useStatements, type Statement, type StatementLine } from '../api'

type DialogState = { kind: 'statement'; statement: Statement | null } | { kind: 'line'; line: StatementLine | null } | null

/** Statements of the rental service provider per unit, with their booking lines and payout. */
export function AbrechnungenPage() {
  const [params, setParams] = useSearchParams()
  const unitId = params.get('unitId') ? Number(params.get('unitId')) : undefined
  const selectedId = params.get('statement') ? Number(params.get('statement')) : null
  const { data: units = [] } = useRentalUnits()
  const { data: statements = [], isLoading } = useStatements(unitId)
  const { data: selected } = useStatement(selectedId)
  const [dialog, setDialog] = useState<DialogState>(null)
  const invalidate = useInvalidateRental()
  const canCreate = units.some((u) => !u.archived && u.myRole !== 'VIEWER')

  function update(changes: Record<string, number | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === undefined ? next.delete(k) : next.set(k, String(v))))
    setParams(next, { replace: true })
  }

  const remove = useMutation({
    mutationFn: ({ url }: { url: string; done: string; clear?: boolean }) => api(url, { method: 'DELETE' }),
    onSuccess: async (_, { done, clear }) => {
      await invalidate()
      toast.success(done)
      if (clear) update({ statement: undefined })
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  return (
    <>
      <PageHeader
        title="Abrechnungen"
        description="Abrechnungen des Dienstleisters je Einheit."
        actions={
          canCreate && (
            <Button onClick={() => setDialog({ kind: 'statement', statement: null })}>
              <PlusIcon />
              Abrechnung
            </Button>
          )
        }
      />
      <div className="mb-4 w-72">
        <IdSelect
          id="st-filter-unit"
          label="Einheit"
          value={unitId}
          options={units.map((u) => ({ id: u.id, name: unitLabel(u) }))}
          onChange={(v) => update({ unitId: v, statement: undefined })}
        />
      </div>
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Datum</TableHead>
              <TableHead>Einheit</TableHead>
              <TableHead>Dienstleister</TableHead>
              <TableHead>Zeitraum</TableHead>
              <TableHead className="text-right">Buchungen</TableHead>
              <TableHead className="text-right">Auszahlung</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {statements.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                  {isLoading ? 'Wird geladen …' : 'Noch keine Abrechnungen.'}
                </TableCell>
              </TableRow>
            ) : (
              statements.map((s) => (
                <TableRow
                  key={s.id}
                  tabIndex={0}
                  className="cursor-pointer"
                  data-state={s.id === selectedId ? 'selected' : undefined}
                  onClick={() => update({ statement: s.id })}
                  onKeyDown={(e) => e.key === 'Enter' && update({ statement: s.id })}
                >
                  <TableCell>{formatDate(s.statementDate)}</TableCell>
                  <TableCell>{unitLabel(s.unit)}</TableCell>
                  <TableCell>{s.provider}</TableCell>
                  <TableCell>
                    {formatDate(s.periodFrom)} – {formatDate(s.periodTo)}
                  </TableCell>
                  <TableCell className="text-right">{s.lineCount}</TableCell>
                  <TableCell className="text-right">
                    <Money amount={s.payout} />
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {selected && (
        <section className="mt-8" aria-label="Abrechnung">
          <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold">
                {selected.provider} · {formatDate(selected.statementDate)}
              </h2>
              <p className="text-sm text-muted-foreground">
                {unitLabel(selected.unit)} · {formatDate(selected.periodFrom)} – {formatDate(selected.periodTo)}
              </p>
            </div>
            {selected.canEdit && (
              <div className="flex flex-wrap gap-2">
                <Button size="sm" onClick={() => setDialog({ kind: 'line', line: null })}>
                  <PlusIcon />
                  Buchung zuordnen
                </Button>
                <Button size="sm" variant="outline" onClick={() => setDialog({ kind: 'statement', statement: selected })}>
                  <PencilIcon />
                  Bearbeiten
                </Button>
                <ConfirmButton
                  question="Abrechnung löschen? Die Buchungen werden wieder geplant."
                  disabled={remove.isPending}
                  onConfirm={() => remove.mutate({ url: `/api/vacationrental/statements/${selected.id}`, done: 'Abrechnung gelöscht.', clear: true })}
                >
                  <Trash2Icon />
                  Löschen
                </ConfirmButton>
              </div>
            )}
          </div>
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
                        {formatDate(l.booking.arrival)} – {formatDate(l.booking.departure)}
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
        </section>
      )}

      {dialog?.kind === 'statement' && (
        <StatementDialog
          statement={dialog.statement}
          defaultUnitId={unitId}
          onSaved={(s) => {
            setDialog(null)
            update({ statement: s.id })
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === 'line' && selected && <StatementLineDialog statement={selected} line={dialog.line} onClose={() => setDialog(null)} />}
    </>
  )
}
