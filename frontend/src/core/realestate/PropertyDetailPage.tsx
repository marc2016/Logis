import { lazy, Suspense, useState } from 'react'
import { useParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { ArchiveIcon, ArchiveRestoreIcon, PencilIcon, PlusIcon, UsersIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ShareDialog } from '@/components/ShareDialog'
import { FullPageSpinner } from '@/components/FullPageSpinner'
import { NotFound } from '@/app/NotFound'
import { api, ApiError } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { roleLabel, useAccounts, useTransactions } from '@/core/ledger/api'
import { TransactionTable } from '@/core/ledger/transactions/TransactionTable'
import { Pagination } from '@/core/ledger/transactions/Pagination'
import { PropertyDialog } from './PropertyDialog'
import { UnitDialog } from './UnitDialog'
import { atLeast, formatAddress, unitUsageLabel, useInvalidateProperties, useProperty, type Unit } from './api'

// The finances section brings the table library; load it with the page section, not the main bundle.
const PropertyFinances = lazy(() => import('./PropertyFinances').then((m) => ({ default: m.PropertyFinances })))

const PAGE_SIZE = 50
const UNIT_ROLES = ['VIEWER', 'EDITOR'] as const
const UNIT_SHARE_TEXT = 'Lesen: Einheit und zugeordnete Buchungen ansehen. Bearbeiten: zusätzlich Buchungen zuordnen. Freigaben verwalten die Owner des Objekts.'
const PROPERTY_SHARE_TEXT = 'Lesen: nur ansehen. Bearbeiten: Einheiten pflegen und Buchungen zuordnen. Owner: zusätzlich Objekt und Freigaben verwalten.'

type DialogState = { kind: 'edit' } | { kind: 'share' } | { kind: 'unit'; unit: Unit | null } | { kind: 'share-unit'; unit: Unit } | null

export function PropertyDetailPage() {
  const propertyId = Number(useParams().propertyId)
  const { data: property, error, isLoading } = useProperty(propertyId)
  const { data: accounts = [] } = useAccounts()
  const [page, setPage] = useState(0)
  const { data: transactions, isLoading: txLoading } = useTransactions({ propertyId, page, size: PAGE_SIZE })
  const [dialog, setDialog] = useState<DialogState>(null)
  const invalidate = useInvalidateProperties()

  const action = useMutation({
    mutationFn: ({ url }: { url: string; done: string }) => api(url, { method: 'POST' }),
    onSuccess: async (_, { done }) => {
      await invalidate()
      toast.success(done)
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  if (isLoading) return <FullPageSpinner />
  if (error instanceof ApiError && error.status === 404) return <NotFound />
  if (!property) return null

  const isOwner = property.myRole === 'OWNER'
  const canEditUnits = atLeast(property.myRole, 'EDITOR')
  const address = formatAddress(property)
  // Units are listed for multi-unit properties, and for users who only see single units.
  const showUnits = property.multiUnit || !property.myRole

  return (
    <>
      <div className="mb-6">
        <p className="text-sm text-muted-foreground">{property.legalEntity.name}</p>
        <h1 className="text-2xl font-semibold tracking-tight">{property.name}</h1>
        <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          {address}
          {property.myRole && <Badge variant="outline">{roleLabel[property.myRole]}</Badge>}
          {property.archived && <Badge variant="secondary">archiviert</Badge>}
          {!property.multiUnit && property.units[0] && <span>{unitUsageLabel[property.units[0].usage]}</span>}
        </div>
      </div>

      <div className="mb-6 flex flex-wrap gap-2">
        {property.myRole && (
          <Button variant="outline" onClick={() => setDialog({ kind: 'share' })}>
            <UsersIcon />
            {isOwner ? 'Freigeben' : 'Freigaben'}
          </Button>
        )}
        {canEditUnits && (
          <Button variant="outline" onClick={() => setDialog({ kind: 'unit', unit: null })}>
            <PlusIcon />
            Einheit hinzufügen
          </Button>
        )}
        {isOwner && (
          <>
            <Button variant="outline" onClick={() => setDialog({ kind: 'edit' })}>
              <PencilIcon />
              Bearbeiten
            </Button>
            <Button
              variant="outline"
              disabled={action.isPending}
              onClick={() =>
                action.mutate({
                  url: `/api/properties/${property.id}/${property.archived ? 'restore' : 'archive'}`,
                  done: property.archived ? 'Objekt wiederhergestellt.' : 'Objekt archiviert.',
                })
              }
            >
              {property.archived ? <ArchiveRestoreIcon /> : <ArchiveIcon />}
              {property.archived ? 'Wiederherstellen' : 'Archivieren'}
            </Button>
          </>
        )}
      </div>

      {showUnits && (
        <section className="mb-8" aria-label="Einheiten">
          <h2 className="mb-2 text-lg font-semibold">Einheiten</h2>
          <ul className="divide-y rounded-lg border">
            {property.units.map((unit) => (
              <li key={unit.id} className="flex flex-wrap items-center gap-3 p-3">
                <div className="min-w-0 flex-1">
                  <div className="font-medium">{unit.name}</div>
                  <div className="text-xs text-muted-foreground">{unitUsageLabel[unit.usage]}</div>
                </div>
                {!property.myRole && <Badge variant="outline">{roleLabel[unit.myRole]}</Badge>}
                {unit.archived && <Badge variant="secondary">archiviert</Badge>}
                {property.multiUnit && (
                  <Button variant="ghost" size="sm" onClick={() => setDialog({ kind: 'share-unit', unit })}>
                    <UsersIcon />
                    {isOwner ? `${unit.name} freigeben` : `Freigaben ${unit.name}`}
                  </Button>
                )}
                {canEditUnits && (
                  <>
                    <Button variant="ghost" size="icon" aria-label={`${unit.name} bearbeiten`} onClick={() => setDialog({ kind: 'unit', unit })}>
                      <PencilIcon />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`${unit.name} ${unit.archived ? 'wiederherstellen' : 'archivieren'}`}
                      disabled={action.isPending}
                      onClick={() =>
                        action.mutate({
                          url: `/api/units/${unit.id}/${unit.archived ? 'restore' : 'archive'}`,
                          done: unit.archived ? 'Einheit wiederhergestellt.' : 'Einheit archiviert.',
                        })
                      }
                    >
                      {unit.archived ? <ArchiveRestoreIcon /> : <ArchiveIcon />}
                    </Button>
                  </>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      <Suspense fallback={<p className="mb-8 text-muted-foreground">Finanzen werden geladen …</p>}>
        <PropertyFinances property={property} />
      </Suspense>

      <h2 className="mb-2 flex flex-wrap items-center gap-2 text-lg font-semibold">
        Zugeordnete Buchungen
        <Badge variant="secondary">nicht im Ergebnis</Badge>
      </h2>
      <TransactionTable items={transactions?.items ?? []} accounts={accounts} isLoading={txLoading} />
      <Pagination page={page} size={PAGE_SIZE} total={transactions?.totalItems ?? 0} onChange={setPage} />

      {dialog?.kind === 'edit' && <PropertyDialog property={property} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'unit' && <UnitDialog propertyId={property.id} unit={dialog.unit} onClose={() => setDialog(null)} />}
      <ShareDialog
        objectType="property"
        objectId={property.id}
        objectName={property.name}
        description={PROPERTY_SHARE_TEXT}
        open={dialog?.kind === 'share'}
        onOpenChange={(o) => setDialog(o ? { kind: 'share' } : null)}
      />
      {dialog?.kind === 'share-unit' && (
        <ShareDialog
          objectType="unit"
          objectId={dialog.unit.id}
          objectName={`${property.name} · ${dialog.unit.name}`}
          roles={[...UNIT_ROLES]}
          description={UNIT_SHARE_TEXT}
          open
          onOpenChange={(o) => !o && setDialog(null)}
        />
      )}
    </>
  )
}
