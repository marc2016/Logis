import { useState } from 'react'
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router'
import { PlusIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Money } from '@/components/Money'
import { PageHeader } from '@/components/PageHeader'
import { formatDate } from '@/core/ledger/transactions/format'
import { IdSelect } from '../RentalFilters'
import { StatementDialog } from '../StatementDialog'
import { backTo, statementPath } from '../BookingPage.paths'
import { unitLabel, useRentalUnits, useStatements } from '../api'

/** Statements of the rental service provider per unit, with their booking lines and payout. */
export function AbrechnungenPage() {
  const [params, setParams] = useSearchParams()
  const unitId = params.get('unitId') ? Number(params.get('unitId')) : undefined
  const oldLink = params.get('statement')
  const { data: units = [] } = useRentalUnits()
  const { data: statements = [], isLoading } = useStatements(unitId)
  const [creating, setCreating] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const canCreate = units.some((u) => !u.archived && u.myRole !== 'VIEWER')

  function update(changes: Record<string, number | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === undefined ? next.delete(k) : next.set(k, String(v))))
    setParams(next, { replace: true })
  }

  const open = (id: number) => navigate(statementPath(id), { state: backTo(location) })

  // Earlier addresses selected a statement in the list; it has its own page now.
  if (oldLink && Number.isInteger(Number(oldLink))) {
    const rest = new URLSearchParams(params)
    rest.delete('statement')
    const listUrl = `${location.pathname}${rest.size ? `?${rest}` : ''}`
    return <Navigate to={statementPath(Number(oldLink))} state={{ back: listUrl }} replace />
  }

  return (
    <>
      <PageHeader
        title="Abrechnungen"
        description="Abrechnungen des Dienstleisters je Einheit."
        actions={
          canCreate && (
            <Button onClick={() => setCreating(true)}>
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
          onChange={(v) => update({ unitId: v })}
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
                  aria-label={`Abrechnung ${s.provider} ${formatDate(s.statementDate)}`}
                  onClick={() => open(s.id)}
                  onKeyDown={(e) => e.key === 'Enter' && open(s.id)}
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

      {creating && (
        <StatementDialog
          statement={null}
          defaultUnitId={unitId}
          onSaved={(saved) => {
            setCreating(false)
            open(saved.id)
          }}
          onClose={() => setCreating(false)}
        />
      )}
    </>
  )
}
