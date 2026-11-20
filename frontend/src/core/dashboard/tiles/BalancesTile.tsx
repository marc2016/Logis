import { Link } from 'react-router'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Money } from '@/components/Money'
import { useAccounts } from '@/core/ledger/api'
import { Loading, Unavailable } from '../TileStates'

export interface BalancesSettings {
  /** Empty or missing: all accounts. */
  accountIds?: number[]
}

function sum(values: string[]) {
  const cents = values.reduce((total, v) => total + Math.round(Number(v) * 100), 0)
  return (cents / 100).toFixed(2)
}

/** „Kontostände“: balances of all or selected accounts, with a total per currency. */
export function BalancesTile({ settings }: { settings: BalancesSettings }) {
  const { data: accounts } = useAccounts()
  if (!accounts) return <Loading />
  const chosen = settings.accountIds ?? []
  // Accounts no longer visible are dropped; only when none is left the tile says so (design D6).
  const shown = chosen.length === 0 ? accounts : accounts.filter((a) => chosen.includes(a.id))
  if (chosen.length > 0 && shown.length === 0) return <Unavailable />
  if (shown.length === 0) return <p className="text-sm text-muted-foreground">Keine Konten.</p>
  const currencies = [...new Set(shown.map((a) => a.currency))]
  return (
    <div className="space-y-2 text-sm">
      <ul className="divide-y">
        {shown.map((a) => (
          <li key={a.id} className="flex items-center justify-between gap-3 py-1.5">
            <Link to={`/finanzen/konten/${a.id}`} className="min-w-0 truncate hover:underline">
              {a.name}
            </Link>
            <Money amount={a.balance} />
          </li>
        ))}
      </ul>
      {shown.length > 1 &&
        currencies.map((c) => (
          <div key={c} className="flex items-center justify-between gap-3 border-t pt-2 font-medium">
            <span>Summe{currencies.length > 1 ? ` ${c}` : ''}</span>
            <Money amount={sum(shown.filter((a) => a.currency === c).map((a) => a.balance))} />
          </div>
        ))}
    </div>
  )
}

export function BalancesSettingsForm({ value, onChange }: { value: BalancesSettings; onChange: (s: BalancesSettings) => void }) {
  const { data: accounts = [] } = useAccounts()
  const chosen = value.accountIds ?? []
  const toggle = (id: number, on: boolean) =>
    onChange({ accountIds: on ? [...chosen, id] : chosen.filter((c) => c !== id) })
  return (
    <fieldset className="space-y-2">
      <legend className="mb-1 text-sm font-medium">Konten</legend>
      <p className="text-xs text-muted-foreground">Ohne Auswahl werden alle Konten gezeigt.</p>
      {accounts.map((a) => (
        <div key={a.id} className="flex items-center gap-2">
          <Checkbox id={`acc-${a.id}`} checked={chosen.includes(a.id)} onCheckedChange={(on) => toggle(a.id, on === true)} />
          <Label htmlFor={`acc-${a.id}`}>{a.name}</Label>
        </div>
      ))}
    </fieldset>
  )
}
