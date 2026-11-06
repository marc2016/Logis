import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeftRightIcon, HouseIcon, PencilIcon, Trash2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Money } from '@/components/Money'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { useInvalidateLedger, writableAccounts, type Account, type Transaction } from '../api'
import { formatDate } from './format'
import { TransactionDialog } from './TransactionDialog'
import { TransferDialog } from './TransferDialog'

interface Props {
  items: Transaction[]
  /** All visible accounts - for rights per row and the selection in the dialog. */
  accounts: Account[]
  showAccount?: boolean
  isLoading?: boolean
}

export function TransactionTable({ items, accounts, showAccount = true, isLoading }: Props) {
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

  const columns = showAccount ? 6 : 5
  return (
    <>
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
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
              <TableRow key={t.id}>
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
                  {canEdit(t) && (
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
      {editing && !editing.transferId && (
        <TransactionDialog transaction={editing} accounts={writable} onClose={() => setEditing(null)} />
      )}
      {editing && editing.transferId && (
        <TransferDialog transaction={editing} accounts={writable} onClose={() => setEditing(null)} />
      )}
    </>
  )
}
