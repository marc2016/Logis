import { useState } from 'react'
import { Link } from 'react-router'
import { PlusIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { PageHeader } from '@/components/PageHeader'
import { roleLabel } from '@/core/ledger/api'
import { PropertyDialog } from './PropertyDialog'
import { formatAddress, unitUsageLabel, useProperties } from './api'

export function PropertiesPage() {
  const [showArchived, setShowArchived] = useState(false)
  const [creating, setCreating] = useState(false)
  const { data: properties = [], isLoading } = useProperties(showArchived)

  return (
    <>
      <PageHeader
        title="Objekte"
        description="Alle Immobilien, die dir gehören oder für dich freigegeben sind."
        actions={
          <Button onClick={() => setCreating(true)}>
            <PlusIcon />
            Objekt anlegen
          </Button>
        }
      />
      <div className="mb-4 flex items-center gap-2">
        <Checkbox id="show-archived" checked={showArchived} onCheckedChange={(v) => setShowArchived(v === true)} />
        <Label htmlFor="show-archived">Archivierte anzeigen</Label>
      </div>
      {isLoading && <p className="text-muted-foreground">Wird geladen …</p>}
      {!isLoading && properties.length === 0 && <p className="text-muted-foreground">Noch keine Objekte. Lege dein erstes Objekt an.</p>}
      <div className="grid gap-6 xl:grid-cols-2">
        {properties.map((p) => (
          <Card key={p.id} role="region" aria-label={p.name}>
            <CardHeader>
              <CardTitle>
                <Link to={`/immobilien/${p.id}`} className="hover:underline">
                  {p.name}
                </Link>
              </CardTitle>
              <CardDescription className="flex flex-wrap items-center gap-2">
                {p.legalEntity.name}
                {formatAddress(p) && <span>· {formatAddress(p)}</span>}
                {p.myRole && p.myRole !== 'OWNER' && <Badge variant="outline">{roleLabel[p.myRole]}</Badge>}
                {p.archived && <Badge variant="secondary">archiviert</Badge>}
              </CardDescription>
            </CardHeader>
            {(p.multiUnit || !p.myRole) && (
              <CardContent className="divide-y p-0">
                {p.units.map((u) => (
                  <div key={u.id} className="flex items-center gap-3 px-6 py-2 text-sm">
                    <span className="flex-1 font-medium">{u.name}</span>
                    <span className="text-muted-foreground">{unitUsageLabel[u.usage]}</span>
                    {!p.myRole && <Badge variant="outline">{roleLabel[u.myRole]}</Badge>}
                    {u.archived && <Badge variant="secondary">archiviert</Badge>}
                  </div>
                ))}
              </CardContent>
            )}
          </Card>
        ))}
      </div>
      {creating && <PropertyDialog property={null} onClose={() => setCreating(false)} />}
    </>
  )
}
