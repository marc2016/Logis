import { useState } from 'react'
import { Link } from 'react-router'
import { PlusIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Money } from '@/components/Money'
import { PageHeader } from '@/components/PageHeader'
import { accountKindLabel, groupByLegalEntity, roleLabel, useAccounts } from '../api'
import { AccountDialog } from './AccountDialog'

export function AccountsPage() {
  const [showArchived, setShowArchived] = useState(false)
  const [creating, setCreating] = useState(false)
  const { data: accounts = [], isLoading } = useAccounts(showArchived)
  const groups = groupByLegalEntity(accounts)

  return (
    <>
      <PageHeader
        title="Konten"
        description="Alle Konten, die dir gehören oder für dich freigegeben sind."
        actions={
          <Button onClick={() => setCreating(true)}>
            <PlusIcon />
            Konto anlegen
          </Button>
        }
      />
      <div className="mb-4 flex items-center gap-2">
        <Checkbox id="show-archived" checked={showArchived} onCheckedChange={(v) => setShowArchived(v === true)} />
        <Label htmlFor="show-archived">Archivierte anzeigen</Label>
      </div>
      {isLoading && <p className="text-muted-foreground">Wird geladen …</p>}
      {!isLoading && groups.length === 0 && (
        <p className="text-muted-foreground">Noch keine Konten. Lege dein erstes Konto an.</p>
      )}
      <div className="grid gap-6 xl:grid-cols-2">
        {groups.map((group) => (
          <Card key={group.id} role="region" aria-label={group.name}>
            <CardHeader>
              <CardTitle>{group.name}</CardTitle>
            </CardHeader>
            <CardContent className="divide-y p-0">
              {group.accounts.map((account) => (
                <Link
                  key={account.id}
                  to={`/finanzen/konten/${account.id}`}
                  className="flex items-center gap-3 px-6 py-3 hover:bg-muted/50"
                >
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-medium">{account.name}</div>
                    <div className="flex flex-wrap gap-1 text-xs text-muted-foreground">
                      {accountKindLabel[account.kind]}
                      {account.myRole !== 'OWNER' && <Badge variant="outline">{roleLabel[account.myRole]}</Badge>}
                      {account.archived && <Badge variant="secondary">archiviert</Badge>}
                    </div>
                  </div>
                  <Money amount={account.balance} className="font-medium" />
                </Link>
              ))}
            </CardContent>
          </Card>
        ))}
      </div>
      {creating && <AccountDialog account={null} onClose={() => setCreating(false)} />}
    </>
  )
}
