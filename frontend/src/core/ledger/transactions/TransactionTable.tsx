import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeftRightIcon, HouseIcon, LinkIcon, PencilIcon, Trash2Icon } from 'lucide-react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Money } from '@/components/Money'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { useInvalidateLedger, writableAccounts, type Account, type Transaction } from '../api'
import { formatDate } from './format'
import { BulkActionBar } from '@/components/BulkActionBar'
import { CategorySelect } from '@/components/CategorySelect'
import { ConfirmButton } from '@/components/ConfirmButton'
import { PageCheckbox, RowCheckbox } from '@/components/selection'
import { TargetSelect } from '@/components/TargetSelect'
import { useBulk } from '@/components/useBulk'
import { useSelection } from '@/components/useSelection'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { NO_TARGET, type Target } from '@/core/realestate/api'
import { TransactionDialog } from './TransactionDialog'
import { TransferDialog } from './TransferDialog'

interface Props {
  items: Transaction[]
  /** All visible accounts - for rights per row and the selection in the dialog. */
  accounts: Account[]
  showAccount?: boolean
  isLoading?: boolean
  /** Identifies the shown page (filter, page number): when it changes, the selection is cleared. */
  selectionKey?: string
}

/** The transaction as the update endpoint expects it, with one field changed. */
function updateBody(t: Transaction, change: Partial<Pick<Transaction, 'categoryId' | 'propertyId' | 'unitId'>>) {
  return {
    accountId: t.accountId,
    bookingDate: t.bookingDate,
    amount: t.amount,
    categoryId: t.categoryId,
    counterparty: t.counterparty,
    purpose: t.purpose,
    note: t.note,
    propertyId: t.propertyId,
    unitId: t.unitId,
    version: t.version,
    ...change,
  }
}

/** Transactions managed by a module: shown with their origin, changed only there (spec transactions). */
const managedSources: Record<string, { label: string; path: (id: number) => string }> = {
  'longtermrental.tenancy': { label: 'Mietzahlung', path: (id) => `/dauervermietung/mietverhaeltnisse/${id}` },
}

export function TransactionTable({ items, accounts, showAccount = true, isLoading, selectionKey = '' }: Props) {
  const [editing, setEditing] = useState<Transaction | null>(null)
  const invalidate = useInvalidateLedger()
  const writable = writableAccounts(accounts)
  const canEdit = (t: Transaction) => t.access === 'FULL' && writable.some((a) => a.id === t.accountId)

  const remove = useMutation({
    mutationFn: (t: Transaction) =>
      t.transferId ? api(`/api/transfers/${t.transferId}`, { method: 'DELETE' }) : api(`/api/transactions/${t.id}`, { method: 'DELETE' }),
    onSuccess: async () => {
      await invalidate()
      toast.success('Gelöscht.')
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const selection = useSelection(selectionKey)
  const bulk = useBulk(selection, invalidate)
  const [bulkDialog, setBulkDialog] = useState<'category' | 'target' | null>(null)
  const [bulkCategory, setBulkCategory] = useState<number | null>(null)
  const [bulkTarget, setBulkTarget] = useState<Target>(NO_TARGET)
  const selectableIds = items.filter(canEdit).map((t) => String(t.id))
  const order = selectableIds
  const selected = items.filter((t) => selection.isSelected(String(t.id)))
  const txId = (t: Transaction) => String(t.id)
  const withSelection = selectableIds.length > 0

  function assign(change: (t: Transaction) => Partial<Transaction>, verb: string) {
    setBulkDialog(null)
    bulk.run(selected, txId, (t) => api(`/api/transactions/${t.id}`, { method: 'PUT', json: updateBody(t, change(t)) }), verb)
  }

  /** Both sides of a transfer are one deletion. */
  function removeSelected() {
    const seen = new Set<string>()
    const unique = selected.filter((t) => !t.transferId || (!seen.has(t.transferId) && seen.add(t.transferId)))
    bulk.run(unique, txId, (t) => (t.transferId ? api(`/api/transfers/${t.transferId}`, { method: 'DELETE' }) : api(`/api/transactions/${t.id}`, { method: 'DELETE' })), 'gelöscht')
  }

  const columns = (showAccount ? 6 : 5) + (withSelection ? 1 : 0)
  return (
    <>
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              {withSelection && (
                <TableHead className="w-10">
                  <PageCheckbox ids={selectableIds} selection={selection} />
                </TableHead>
              )}
              <TableHead className="w-28">Datum</TableHead>
              {showAccount && <TableHead className="hidden md:table-cell">Konto</TableHead>}
              <TableHead>Beschreibung</TableHead>
              <TableHead className="hidden sm:table-cell">Kategorie</TableHead>
              <TableHead className="text-right">Betrag</TableHead>
              <TableHead className="w-24">
                <span className="sr-only">Aktionen</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={columns} className="text-muted-foreground">
                  Wird geladen …
                </TableCell>
              </TableRow>
            )}
            {!isLoading && items.length === 0 && (
              <TableRow>
                <TableCell colSpan={columns} className="text-muted-foreground">
                  Keine Transaktionen.
                </TableCell>
              </TableRow>
            )}
            {items.map((t) => (
              <TableRow key={t.id} data-state={selection.isSelected(String(t.id)) ? 'selected' : undefined}>
                {withSelection && (
                  <TableCell className="w-10">
                    {canEdit(t) && (
                      <RowCheckbox
                        id={String(t.id)}
                        label={`Transaktion ${formatDate(t.bookingDate)} ${t.counterparty ?? t.counterAccountName ?? ''}`.trim()}
                        selection={selection}
                        order={order}
                      />
                    )}
                  </TableCell>
                )}
                <TableCell className="tabular-nums">{formatDate(t.bookingDate)}</TableCell>
                {showAccount && (
                  <TableCell className="hidden md:table-cell">
                    {t.access === 'TARGET' ? <span className="text-muted-foreground">über {t.targetName}</span> : t.accountName}
                  </TableCell>
                )}
                <TableCell>
                  {t.transferId ? (
                    <span className="flex items-center gap-2">
                      <Badge variant="secondary">
                        <ArrowLeftRightIcon />
                        Umbuchung
                      </Badge>
                      <span className="text-muted-foreground">
                        {t.amount.startsWith('-') ? 'nach' : 'von'} {t.counterAccountName}
                      </span>
                    </span>
                  ) : (
                    <>
                      <div className="font-medium">{t.counterparty ?? '–'}</div>
                      {t.purpose && <div className="text-xs text-muted-foreground">{t.purpose}</div>}
                      {t.targetName && (
                        <div className="flex items-center gap-1 text-xs text-muted-foreground">
                          <HouseIcon className="size-3" />
                          {t.targetName}
                        </div>
                      )}
                    </>
                  )}
                </TableCell>
                <TableCell className="hidden sm:table-cell">{t.categoryName ?? (t.transferId ? '' : <span className="text-muted-foreground">ohne</span>)}</TableCell>
                <TableCell className="text-right">
                  <Money amount={t.amount} />
                </TableCell>
                <TableCell className="text-right">
                  {t.source && (
                    <Link
                      to={managedSources[t.source.type]?.path(t.source.id) ?? '#'}
                      className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground hover:underline"
                    >
                      <LinkIcon className="size-3" />
                      {managedSources[t.source.type]?.label ?? 'Verknüpft'}
                    </Link>
                  )}
                  {canEdit(t) && !t.source && (
                    <>
                      <Button variant="ghost" size="icon" aria-label="Transaktion bearbeiten" onClick={() => setEditing(t)}>
                        <PencilIcon />
                      </Button>
                      <Button variant="ghost" size="icon" aria-label="Transaktion löschen" onClick={() => remove.mutate(t)}>
                        <Trash2Icon />
                      </Button>
                    </>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <BulkActionBar selection={selection} busy={bulk.busy}>
        <Button size="sm" variant="outline" disabled={bulk.busy} onClick={() => setBulkDialog('category')}>
          Kategorie zuordnen
        </Button>
        <Button size="sm" variant="outline" disabled={bulk.busy} onClick={() => setBulkDialog('target')}>
          Objekt zuordnen
        </Button>
        <ConfirmButton
          question={`${selection.ids.size} ${selection.ids.size === 1 ? 'Eintrag' : 'Einträge'} löschen?`}
          disabled={bulk.busy}
          onConfirm={removeSelected}
        >
          Löschen
        </ConfirmButton>
      </BulkActionBar>
      {bulkDialog && (
        <Dialog open onOpenChange={(o) => !o && setBulkDialog(null)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{bulkDialog === 'category' ? 'Kategorie zuordnen' : 'Objekt zuordnen'}</DialogTitle>
            </DialogHeader>
            <div className="space-y-2">
              <Label htmlFor="bulk-choice">{bulkDialog === 'category' ? 'Kategorie' : 'Objekt oder Einheit'}</Label>
              {bulkDialog === 'category' ? (
                <CategorySelect id="bulk-choice" value={bulkCategory} onChange={setBulkCategory} />
              ) : (
                <TargetSelect id="bulk-choice" value={bulkTarget} onChange={setBulkTarget} mode="assign" />
              )}
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setBulkDialog(null)}>
                Abbrechen
              </Button>
              <Button
                type="button"
                onClick={() =>
                  bulkDialog === 'category'
                    ? assign(() => ({ categoryId: bulkCategory }), 'zugeordnet')
                    : assign(() => ({ propertyId: bulkTarget.propertyId, unitId: bulkTarget.unitId }), 'zugeordnet')
                }
              >
                Zuordnen
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
      {editing && !editing.transferId && (
        <TransactionDialog transaction={editing} accounts={writable} onClose={() => setEditing(null)} />
      )}
      {editing && editing.transferId && (
        <TransferDialog transaction={editing} accounts={writable} onClose={() => setEditing(null)} />
      )}
    </>
  )
}
