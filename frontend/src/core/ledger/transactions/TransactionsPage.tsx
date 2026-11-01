import { useDeferredValue, useState } from 'react'
import { ArrowLeftRightIcon, PlusIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { CategorySelect } from '@/components/CategorySelect'
import { Money } from '@/components/Money'
import { PageHeader } from '@/components/PageHeader'
import { useAccounts, useTransactions, writableAccounts, type TransactionFilter } from '../api'
import { Pagination } from './Pagination'
import { TransactionDialog } from './TransactionDialog'
import { TransactionTable } from './TransactionTable'
import { TransferDialog } from './TransferDialog'

const PAGE_SIZE = 50
const ALL = 'all'

export function TransactionsPage() {
  const { data: accounts = [] } = useAccounts(true)
  const [accountId, setAccountId] = useState<string>(ALL)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [uncategorized, setUncategorized] = useState(false)
  const [search, setSearch] = useState('')
  const q = useDeferredValue(search)
  const [page, setPage] = useState(0)
  const [dialog, setDialog] = useState<'transaction' | 'transfer' | null>(null)

  const filter: TransactionFilter = {
    accountId: accountId === ALL ? undefined : Number(accountId),
    from: from || undefined,
    to: to || undefined,
    categoryId: uncategorized ? undefined : (categoryId ?? undefined),
    uncategorized: uncategorized || undefined,
    q: q.trim() || undefined,
    page,
    size: PAGE_SIZE,
  }
  const { data, isLoading } = useTransactions(filter)
  const writable = writableAccounts(accounts)
  // Every filter change starts again on page 1.
  const reset = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v)
    setPage(0)
  }

  return (
    <>
      <PageHeader
        title="Transaktionen"
        description="Alle Buchungen der Konten, die du sehen darfst."
        actions={
          writable.length > 0 && (
            <div className="flex gap-2">
              <Button onClick={() => setDialog('transaction')}>
                <PlusIcon />
                Transaktion erfassen
              </Button>
              <Button variant="outline" onClick={() => setDialog('transfer')}>
                <ArrowLeftRightIcon />
                Umbuchung
              </Button>
            </div>
          )
        }
      />

      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-6" role="search">
        <div className="space-y-1 lg:col-span-2">
          <Label htmlFor="f-account">Konto</Label>
          <Select value={accountId} onValueChange={reset(setAccountId)}>
            <SelectTrigger id="f-account" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Alle Konten</SelectItem>
              {accounts.map((a) => (
                <SelectItem key={a.id} value={String(a.id)}>
                  {a.name} – {a.legalEntity.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="f-from">Von</Label>
          <Input id="f-from" type="date" value={from} onChange={(e) => reset(setFrom)(e.target.value)} />
        </div>
        <div className="space-y-1">
          <Label htmlFor="f-to">Bis</Label>
          <Input id="f-to" type="date" value={to} onChange={(e) => reset(setTo)(e.target.value)} />
        </div>
        <div className="space-y-1 lg:col-span-2">
          <Label htmlFor="f-category">Kategorie</Label>
          <CategorySelect id="f-category" value={categoryId} onChange={reset(setCategoryId)} noneLabel="Alle Kategorien" />
        </div>
        <div className="space-y-1 sm:col-span-2 lg:col-span-4">
          <Label htmlFor="f-search">Suche</Label>
          <Input id="f-search" placeholder="Gegenpartei, Zweck oder Notiz" value={search} onChange={(e) => reset(setSearch)(e.target.value)} />
        </div>
        <div className="flex items-end gap-2 pb-2 lg:col-span-2">
          <Checkbox id="f-uncategorized" checked={uncategorized} onCheckedChange={(v) => reset(setUncategorized)(v === true)} />
          <Label htmlFor="f-uncategorized">Nur ohne Kategorie</Label>
        </div>
      </div>

      {data && (
        <Card className="mb-4">
          <CardContent className="flex flex-wrap gap-x-8 gap-y-2 py-4 text-sm" aria-label="Summen">
            <span>
              Einnahmen: <Money amount={data.income} className="font-medium" />
            </span>
            <span>
              Ausgaben: <Money amount={data.expense} className="font-medium" />
            </span>
            <span className="text-muted-foreground">{data.totalItems} Transaktionen · ohne Umbuchungen</span>
          </CardContent>
        </Card>
      )}

      <TransactionTable items={data?.items ?? []} accounts={accounts} isLoading={isLoading} />
      <Pagination page={page} size={PAGE_SIZE} total={data?.totalItems ?? 0} onChange={setPage} />

      {dialog === 'transaction' && <TransactionDialog transaction={null} accounts={writable} onClose={() => setDialog(null)} />}
      {dialog === 'transfer' && <TransferDialog transaction={null} accounts={writable} onClose={() => setDialog(null)} />}
    </>
  )
}
