import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeftIcon, PencilIcon, Trash2Icon, TrendingUpIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ConfirmButton } from '@/components/ConfirmButton'
import { DocumentList } from '@/components/DocumentList'
import { Money } from '@/components/Money'
import { ApiError, api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/core/ledger/transactions/format'
import { EndDialog } from '../EndDialog'
import { RentDialog } from '../RentDialog'
import { StateBadge } from '../StateBadge'
import { TenancyDialog } from '../TenancyDialog'
import { period } from '../format'
import { backLabel, TENANCY_LIST, type BackState } from '../paths'
import { TENANCIES, TENANCY_DOCUMENT_TYPE, tenantNames, unitLabel, useInvalidateTenancies, useTenancy, type Rent, type Tenant } from '../api'

type DialogState = { kind: 'edit' } | { kind: 'end' } | { kind: 'rent'; term: Rent | null } | null

function contactLines(tenant: Tenant) {
  const place = [tenant.postalCode, tenant.city].filter(Boolean).join(' ')
  return [tenant.street, place, tenant.country].filter(Boolean) as string[]
}

/** A tenancy as its own page: tenants, rent history, deposit, documents and actions (design D6). */
export function TenancyPage() {
  const { tenancyId } = useParams()
  const id = Number(tenancyId)
  const location = useLocation()
  const navigate = useNavigate()
  const back = (location.state as BackState | null)?.back ?? TENANCY_LIST
  const { data: tenancy, error, isLoading } = useTenancy(Number.isFinite(id) ? id : null)
  const [dialog, setDialog] = useState<DialogState>(null)
  const invalidate = useInvalidateTenancies()

  const remove = useMutation({
    mutationFn: ({ url }: { url: string; done: string; leave?: boolean }) => api(url, { method: 'DELETE' }),
    onSuccess: async (_, { done, leave }) => {
      // Leave first: the deleted tenancy must not be loaded again.
      if (leave) navigate(back)
      await invalidate()
      toast.success(done)
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  const backLink = (
    <Link to={back} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ArrowLeftIcon className="size-4" /> {backLabel(back)}
    </Link>
  )

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96" />
      </div>
    )
  }
  if (!tenancy) {
    const notFound = !Number.isFinite(id) || (error instanceof ApiError && error.status === 404)
    return (
      <div className="space-y-4">
        {backLink}
        <p role="alert">{notFound ? 'Mietverhältnis nicht gefunden' : errorMessage(error)}</p>
      </div>
    )
  }

  const base = `${TENANCIES}/${tenancy.id}`
  const { canEdit } = tenancy

  return (
    <div className="space-y-8">
      {backLink}
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 space-y-2">
          <h1 className="text-2xl font-semibold tracking-tight break-words">{tenantNames(tenancy)}</h1>
          <dl className="flex flex-wrap items-center gap-x-6 gap-y-1 text-sm" aria-label="Wichtige Angaben">
            <div>
              <dt className="sr-only">Einheit</dt>
              <dd>{unitLabel(tenancy.unit)}</dd>
            </div>
            <div>
              <dt className="sr-only">Zeitraum</dt>
              <dd className="font-medium">{period(tenancy)}</dd>
            </div>
            {tenancy.currentRent && (
              <div>
                <dt className="sr-only">Aktuelle Miete</dt>
                <dd className="font-medium">
                  Miete <Money amount={tenancy.currentRent.total} colored={false} />
                </dd>
              </div>
            )}
            <div>
              <dt className="sr-only">Status</dt>
              <dd>
                <StateBadge state={tenancy.state} />
              </dd>
            </div>
          </dl>
        </div>
        {canEdit && (
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="outline" onClick={() => setDialog({ kind: 'edit' })}>
              <PencilIcon />
              Bearbeiten
            </Button>
            {!tenancy.endDate && (
              <Button size="sm" variant="outline" onClick={() => setDialog({ kind: 'end' })}>
                Beenden
              </Button>
            )}
            {tenancy.state === 'ENDED' && !tenancy.tenantsDeleted && (
              <ConfirmButton
                question="Namen und Kontaktdaten der Mieter endgültig löschen? Dokumente bleiben erhalten."
                disabled={remove.isPending}
                onConfirm={() => remove.mutate({ url: `${base}/tenant-data`, done: 'Mieterdaten gelöscht. Dokumente bitte bei Bedarf einzeln löschen.' })}
              >
                Mieterdaten löschen
              </ConfirmButton>
            )}
            <ConfirmButton
              question="Mietverhältnis mit Mietern, Mietverlauf und Dokumenten löschen?"
              disabled={remove.isPending}
              onConfirm={() => remove.mutate({ url: base, done: 'Mietverhältnis gelöscht.', leave: true })}
            >
              <Trash2Icon />
              Löschen
            </ConfirmButton>
          </div>
        )}
      </header>

      <section className="space-y-3" aria-labelledby="tenancy-tenants">
        <h2 id="tenancy-tenants" className="text-lg font-semibold">
          Mieter
        </h2>
        {tenancy.tenantsDeleted ? (
          <p className="text-sm text-muted-foreground">Mieterdaten gelöscht</p>
        ) : (
          <ul className="grid gap-3 @2xl:grid-cols-2">
            {tenancy.tenants.map((tenant, i) => (
              <li key={tenant.id ?? i} className="min-w-0 space-y-1 rounded-lg border p-4 text-sm">
                <p className="font-medium break-words">{tenant.name}</p>
                {contactLines(tenant).map((line) => (
                  <p key={line} className="break-words text-muted-foreground">
                    {line}
                  </p>
                ))}
                {tenant.phone && (
                  <p>
                    <a href={`tel:${tenant.phone}`} className="underline-offset-4 hover:underline">
                      {tenant.phone}
                    </a>
                  </p>
                )}
                {tenant.email && (
                  <p className="break-all">
                    <a href={`mailto:${tenant.email}`} className="underline-offset-4 hover:underline">
                      {tenant.email}
                    </a>
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3" aria-labelledby="tenancy-rent">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="tenancy-rent" className="text-lg font-semibold">
            Mietverlauf
          </h2>
          {canEdit && (
            <Button size="sm" onClick={() => setDialog({ kind: 'rent', term: null })}>
              <TrendingUpIcon />
              Miete ändern
            </Button>
          )}
        </div>
        <div className="rounded-lg border">
          <Table aria-label="Mietverlauf">
            <TableHeader>
              <TableRow>
                <TableHead>Gültig ab</TableHead>
                <TableHead className="text-right">Kaltmiete</TableHead>
                <TableHead className="text-right">Nebenkosten</TableHead>
                <TableHead>Weitere Posten</TableHead>
                <TableHead className="text-right">Gesamt</TableHead>
                {canEdit && <TableHead className="w-24" />}
              </TableRow>
            </TableHeader>
            <TableBody>
              {tenancy.rentTerms.map((term, i) => (
                <TableRow key={term.id} data-state={term.id === tenancy.currentRent?.id ? 'selected' : undefined}>
                  <TableCell className="whitespace-nowrap">
                    {formatDate(term.validFrom)}
                    {term.id === tenancy.currentRent?.id && tenancy.state === 'RUNNING' && <span className="ml-2 text-xs text-muted-foreground">aktuell</span>}
                  </TableCell>
                  <TableCell className="text-right">
                    <Money amount={term.coldRent} colored={false} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Money amount={term.utilitiesAdvance} colored={false} />
                  </TableCell>
                  <TableCell>
                    {term.others.length === 0
                      ? '–'
                      : term.others.map((o) => (
                          <span key={o.label} className="mr-3 inline-block">
                            {o.label} <Money amount={o.amount} colored={false} />
                          </span>
                        ))}
                  </TableCell>
                  <TableCell className="text-right font-medium">
                    <Money amount={term.total} colored={false} />
                  </TableCell>
                  {canEdit && (
                    <TableCell className="text-right whitespace-nowrap">
                      <Button variant="ghost" size="icon" aria-label={`Miete ab ${formatDate(term.validFrom)} korrigieren`} onClick={() => setDialog({ kind: 'rent', term })}>
                        <PencilIcon />
                      </Button>
                      {i > 0 && (
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`Miete ab ${formatDate(term.validFrom)} löschen`}
                          disabled={remove.isPending}
                          onClick={() => remove.mutate({ url: `${base}/rent-terms/${term.id}`, done: 'Miete gelöscht.' })}
                        >
                          <Trash2Icon />
                        </Button>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </section>

      <section className="space-y-2" aria-labelledby="tenancy-deposit">
        <h2 id="tenancy-deposit" className="text-lg font-semibold">
          Kaution
        </h2>
        {tenancy.depositAmount ? (
          <p className="text-sm">
            <Money amount={tenancy.depositAmount} colored={false} className="font-medium" />{' '}
            {tenancy.depositReceivedOn ? (
              <span className="text-muted-foreground">erhalten am {formatDate(tenancy.depositReceivedOn)}</span>
            ) : (
              <span className="font-medium text-amber-700 dark:text-amber-400">offen</span>
            )}
          </p>
        ) : (
          <p className="text-sm text-muted-foreground">Keine Kaution vereinbart.</p>
        )}
      </section>

      {canEdit && (
        <section className="space-y-2" aria-labelledby="tenancy-documents">
          <h2 id="tenancy-documents" className="text-lg font-semibold">
            Dokumente
          </h2>
          <DocumentList ownerType={TENANCY_DOCUMENT_TYPE} ownerId={tenancy.id} canEdit />
        </section>
      )}

      {dialog?.kind === 'edit' && <TenancyDialog tenancy={tenancy} onSaved={() => setDialog(null)} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'end' && <EndDialog tenancy={tenancy} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'rent' && <RentDialog tenancy={tenancy} term={dialog.term} onClose={() => setDialog(null)} />}
    </div>
  )
}
