import { useState } from 'react'
import { useParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { ArchiveIcon, ArchiveRestoreIcon, ArrowLeftRightIcon, PencilIcon, PlusIcon, UsersIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Money } from '@/components/Money'
import { ShareDialog } from '@/components/ShareDialog'
import { FullPageSpinner } from '@/components/FullPageSpinner'
import { NotFound } from '@/app/NotFound'
import { api, ApiError } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { accountKindLabel, roleLabel, useAccount, useAccounts, useInvalidateLedger, useTransactions, writableAccounts } from '../api'
import { TransactionTable } from '../transactions/TransactionTable'
import { TransactionDialog } from '../transactions/TransactionDialog'
import { TransferDialog } from '../transactions/TransferDialog'
import { Pagination } from '../transactions/Pagination'
import { AccountDialog } from './AccountDialog'

const PAGE_SIZE = 50

export function AccountDetailPage() {
  const accountId = Number(useParams().accountId)
  const { data: account, error, isLoading } = useAccount(accountId)
  const { data: accounts = [] } = useAccounts()
  const [page, setPage] = useState(0)
  const { data: transactions, isLoading: txLoading } = useTransactions({ accountId, page, size: PAGE_SIZE })
  const [dialog, setDialog] = useState<'edit' | 'share' | 'transaction' | 'transfer' | null>(null)
  const invalidate = useInvalidateLedger()

  const toggleArchived = useMutation({
    mutationFn: () =>
      api(`/api/accounts/${accountId}`, {
        method: 'PUT',
        json: { name: account!.name, kind: account!.kind, iban: account!.iban, openingBalance: account!.openingBalance, archived: !account!.archived },
      }),
    onSuccess: async () => {
      await invalidate()
      toast.success(account!.archived ? 'Konto wiederhergestellt.' : 'Konto archiviert.')
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  if (isLoading) return <FullPageSpinner />
  if (error instanceof ApiError && error.status === 404) return <NotFound />
  if (!account) return null

  const isOwner = account.myRole === 'OWNER'
  const canBook = account.myRole !== 'VIEWER' && !account.archived
  // In the dialog: this account plus all other writable ones (for transfers/moving).
  const writable = writableAccounts(accounts)

  return (
    <>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm text-muted-foreground">{account.legalEntity.name}</p>
          <h1 className="text-2xl font-semibold tracking-tight">{account.name}</h1>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            {accountKindLabel[account.kind]}
            {account.iban && <span className="font-mono">{account.iban}</span>}
            <Badge variant="outline">{roleLabel[account.myRole]}</Badge>
            {account.archived && <Badge variant="secondary">archiviert</Badge>}
          </div>
        </div>
        <div className="text-right">
          <p className="text-sm text-muted-foreground">Saldo</p>
          <Money amount={account.balance} className="text-2xl font-semibold" colored={false} />
        </div>
      </div>

      <div className="mb-4 flex flex-wrap gap-2">
        {canBook && (
          <>
            <Button onClick={() => setDialog('transaction')}>
              <PlusIcon />
              Transaktion erfassen
            </Button>
            <Button variant="outline" onClick={() => setDialog('transfer')}>
              <ArrowLeftRightIcon />
              Umbuchung
            </Button>
          </>
        )}
        <Button variant="outline" onClick={() => setDialog('share')}>
          <UsersIcon />
          {isOwner ? 'Freigeben' : 'Freigaben'}
        </Button>
        {isOwner && (
          <>
            <Button variant="outline" onClick={() => setDialog('edit')}>
              <PencilIcon />
              Bearbeiten
            </Button>
            <Button variant="outline" onClick={() => toggleArchived.mutate()} disabled={toggleArchived.isPending}>
              {account.archived ? <ArchiveRestoreIcon /> : <ArchiveIcon />}
              {account.archived ? 'Wiederherstellen' : 'Archivieren'}
            </Button>
          </>
        )}
      </div>

      <TransactionTable items={transactions?.items ?? []} accounts={accounts} showAccount={false} isLoading={txLoading} />
      <Pagination page={page} size={PAGE_SIZE} total={transactions?.totalItems ?? 0} onChange={setPage} />

      {dialog === 'edit' && <AccountDialog account={account} onClose={() => setDialog(null)} />}
      <ShareDialog objectType="account" objectId={account.id} objectName={account.name} open={dialog === 'share'} onOpenChange={(o) => setDialog(o ? 'share' : null)} />
      {dialog === 'transaction' && <TransactionDialog transaction={null} accounts={writable} defaultAccountId={account.id} onClose={() => setDialog(null)} />}
      {dialog === 'transfer' && <TransferDialog transaction={null} accounts={writable} defaultFromAccountId={account.id} onClose={() => setDialog(null)} />}
    </>
  )
}
