import { Link } from 'react-router'
import { Money } from '@/components/Money'
import { useAccounts, useTransactions } from '@/core/ledger/api'
import { formatDate } from '@/core/ledger/transactions/format'
import { NumberChoice, ObjectChoice } from '../SettingsFields'
import { Loading, Unavailable } from '../TileStates'

export interface TransactionsSettings {
  count?: number
  accountId?: number
}

/** „Letzte Transaktionen“: the latest 5 or 10 transactions of one account or all. */
export function TransactionsTile({ settings }: { settings: TransactionsSettings }) {
  const { data: accounts } = useAccounts()
  const missing = settings.accountId !== undefined && accounts !== undefined && !accounts.some((a) => a.id === settings.accountId)
  const { data } = useTransactions({ accountId: missing ? undefined : settings.accountId, size: settings.count ?? 5 })
  if (missing) return <Unavailable />
  if (!data || !accounts) return <Loading />
  if (data.items.length === 0) return <p className="text-sm text-muted-foreground">Keine Transaktionen.</p>
  return (
    <div className="space-y-2 text-sm">
      <ul className="divide-y">
        {data.items.map((t) => (
          <li key={t.id} className="flex items-center justify-between gap-3 py-1.5">
            <span className="w-20 shrink-0 tabular-nums text-muted-foreground">{formatDate(t.bookingDate)}</span>
            <span className="min-w-0 flex-1 truncate">{t.counterparty ?? t.purpose ?? t.categoryName ?? '–'}</span>
            <Money amount={t.amount} />
          </li>
        ))}
      </ul>
      <Link to="/finanzen/transaktionen" className="inline-block text-sm hover:underline">
        Alle Transaktionen
      </Link>
    </div>
  )
}

export function TransactionsSettingsForm({ value, onChange }: { value: TransactionsSettings; onChange: (s: TransactionsSettings) => void }) {
  const { data: accounts = [] } = useAccounts()
  return (
    <div className="space-y-4">
      <NumberChoice id="tx-count" label="Anzahl" value={value.count ?? 5} options={[5, 10]} unit="Einträge"
        onChange={(count) => onChange({ ...value, count })} />
      <ObjectChoice id="tx-account" label="Konto" value={value.accountId} allLabel="Alle Konten"
        options={accounts.map((a) => ({ id: a.id, name: a.name }))} onChange={(accountId) => onChange({ ...value, accountId })} />
    </div>
  )
}
