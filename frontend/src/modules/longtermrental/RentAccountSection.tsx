import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Money } from '@/components/Money'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatEUR } from '@/lib/money'
import { formatDate } from '@/core/ledger/transactions/format'
import { PaymentDialog } from './PaymentDialog'
import { TENANCIES, useInvalidateAfterPayment, useRentAccount, type RentPayment } from './api'

const monthName = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric', timeZone: 'UTC' })

function monthLabel(month: string) {
  const [y, m] = month.split('-').map(Number)
  return monthName.format(new Date(Date.UTC(y, m - 1, 1)))
}

/** „Mietkonto“: what is due and paid per month, the payments and the arrears or credit (spec rent-payments). */
export function RentAccountSection({ tenancyId }: { tenancyId: number }) {
  const { data, isLoading } = useRentAccount(tenancyId)
  const [dialog, setDialog] = useState<{ payment: RentPayment | null } | null>(null)
  const invalidate = useInvalidateAfterPayment()
  const remove = useMutation({
    mutationFn: (payment: RentPayment) => api(`${TENANCIES}/${tenancyId}/payments/${payment.id}`, { method: 'DELETE' }),
    onSuccess: async () => {
      await invalidate()
      toast.success('Zahlung gelöscht.')
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  return (
    <section className="space-y-3" aria-labelledby="tenancy-payments">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="tenancy-payments" className="text-lg font-semibold">
          Mietkonto
        </h2>
        {data?.canEdit && (
          <Button size="sm" onClick={() => setDialog({ payment: null })}>
            <PlusIcon />
            Zahlung erfassen
          </Button>
        )}
      </div>
      {isLoading || !data ? (
        <Skeleton className="h-24 w-full" />
      ) : (
        <>
          <dl className="flex flex-wrap gap-x-6 gap-y-1 text-sm" aria-label="Stand des Mietkontos">
            <div>
              <dt className="text-muted-foreground">Soll</dt>
              <dd className="font-medium">{formatEUR(data.due)}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Gezahlt</dt>
              <dd className="font-medium">{formatEUR(data.paid)}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">{data.credit !== '0.00' ? 'Guthaben' : 'Rückstand'}</dt>
              <dd className={data.overdue ? 'font-semibold text-destructive' : 'font-medium'}>
                {formatEUR(data.credit !== '0.00' ? data.credit : data.arrears)}
                {data.overdue && (
                  <Badge variant="destructive" className="ml-2">
                    überfällig
                  </Badge>
                )}
              </dd>
            </div>
          </dl>
          {data.months.length === 0 ? (
            <p className="text-sm text-muted-foreground">Noch keine Miete fällig.</p>
          ) : (
            <div className="rounded-lg border">
              <Table aria-label="Monate">
                <TableHeader>
                  <TableRow>
                    <TableHead>Monat</TableHead>
                    <TableHead className="text-right">Soll</TableHead>
                    <TableHead className="text-right">Gezahlt</TableHead>
                    <TableHead className="text-right">Offen</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.months.map((m) => (
                    <TableRow key={m.month} data-overdue={m.overdue ? 'true' : undefined}>
                      <TableCell>
                        {monthLabel(m.month)}
                        {m.overdue && (
                          <Badge variant="destructive" className="ml-2">
                            überfällig
                          </Badge>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <Money amount={m.due} colored={false} />
                      </TableCell>
                      <TableCell className="text-right">
                        <Money amount={m.paid} colored={false} />
                      </TableCell>
                      <TableCell className="text-right font-medium">
                        <Money amount={m.open} colored={false} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
          <h3 className="text-sm font-medium">Zahlungen</h3>
          {data.payments.length === 0 ? (
            <p className="text-sm text-muted-foreground">Noch keine Zahlung erfasst.</p>
          ) : (
            <div className="rounded-lg border">
              <Table aria-label="Zahlungen">
                <TableHeader>
                  <TableRow>
                    <TableHead>Datum</TableHead>
                    <TableHead className="text-right">Betrag</TableHead>
                    {data.canEdit && <TableHead>Konto</TableHead>}
                    {data.canEdit && <TableHead>Notiz</TableHead>}
                    {data.canEdit && <TableHead className="w-24" />}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.payments.map((p) => (
                    <TableRow key={p.id}>
                      <TableCell>{formatDate(p.date)}</TableCell>
                      <TableCell className="text-right">
                        <Money amount={p.amount} colored={false} />
                      </TableCell>
                      {data.canEdit && <TableCell>{p.accountName ?? '–'}</TableCell>}
                      {data.canEdit && <TableCell className="text-muted-foreground">{p.note ?? ''}</TableCell>}
                      {data.canEdit && (
                        <TableCell className="text-right">
                          <Button variant="ghost" size="icon" aria-label={`Zahlung vom ${formatDate(p.date)} bearbeiten`} onClick={() => setDialog({ payment: p })}>
                            <PencilIcon />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Zahlung vom ${formatDate(p.date)} löschen`}
                            disabled={remove.isPending}
                            onClick={() => remove.mutate(p)}
                          >
                            <Trash2Icon />
                          </Button>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </>
      )}
      {dialog && <PaymentDialog tenancyId={tenancyId} payment={dialog.payment} onClose={() => setDialog(null)} />}
    </section>
  )
}
