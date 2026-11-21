import { Link, useLocation } from 'react-router'
import { PlusIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Money } from '@/components/Money'
import { PageHeader } from '@/components/PageHeader'
import { formatDate } from '@/core/ledger/transactions/format'
import { period } from '../format'
import { backTo, newTenancyPath, tenancyPath } from '../paths'
import { tenantNames, unitLabel, useLongTermUnits } from '../api'

/** Every long-term unit with its tenant and rent, or since when it is vacant (spec tenancies, design D4). */
export function EinheitenPage() {
  const { data: units = [], isLoading } = useLongTermUnits()
  const location = useLocation()
  const back = backTo(location)

  return (
    <>
      <PageHeader title="Einheiten" description="Die Einheiten in Dauermiete mit Mieter oder Leerstand." />
      {!isLoading && units.length === 0 && (
        <p className="text-sm text-muted-foreground">
          Keine Einheiten in Dauermiete. Die Nutzung „Dauermiete“ wird an der Einheit unter „Objekte“ gewählt.
        </p>
      )}
      <ul className="grid gap-4 @2xl:grid-cols-2 @5xl:grid-cols-3" aria-label="Einheiten">
        {units.map(({ unit, canEdit, tenancy, vacantSince, next }) => (
          <li key={unit.id} className="min-w-0">
            <Card className="h-full">
              <CardHeader>
                <CardTitle>
                  <h2 className="text-base font-semibold">{unitLabel(unit)}</h2>
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                {tenancy ? (
                  <>
                    <p>
                      <Link to={tenancyPath(tenancy.id)} state={back} className="font-medium underline-offset-4 hover:underline">
                        {tenantNames(tenancy)}
                      </Link>
                    </p>
                    <p className="text-muted-foreground">{period(tenancy)}</p>
                    {tenancy.currentRent && (
                      <p>
                        Miete <Money amount={tenancy.currentRent.total} colored={false} className="font-medium" />
                      </p>
                    )}
                  </>
                ) : (
                  <>
                    <p className="font-medium text-amber-700 dark:text-amber-400">
                      {vacantSince ? `Leerstand seit ${formatDate(vacantSince)}` : 'Leerstand'}
                    </p>
                    {next && (
                      <p>
                        <Link to={tenancyPath(next.id)} state={back} className="underline-offset-4 hover:underline">
                          ab {formatDate(next.startDate)} vermietet
                        </Link>
                      </p>
                    )}
                    {canEdit && !next && (
                      <Button asChild variant="outline" size="sm">
                        <Link to={newTenancyPath(unit.id)} aria-label={`Mietverhältnis für ${unitLabel(unit)} anlegen`}>
                          <PlusIcon />
                          Mietverhältnis
                        </Link>
                      </Button>
                    )}
                  </>
                )}
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>
    </>
  )
}
