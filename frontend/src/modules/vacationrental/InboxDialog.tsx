import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ConfirmButton } from '@/components/ConfirmButton'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { importResultLabel, mailTypeLabel, RETRYABLE, unitLabel, useInvalidateRental, useRentalUnits, type InboxItem } from './api'

/** Details of a fetched mail with the actions for its result. */
export function InboxDialog({ item, onClose }: { item: InboxItem; onClose: () => void }) {
  const navigate = useNavigate()
  const { data: units = [] } = useRentalUnits()
  const editable = units.filter((u) => !u.archived && u.myRole !== 'VIEWER')
  const [unitId, setUnitId] = useState('')
  const invalidate = useInvalidateRental()
  const { data: text } = useQuery({
    queryKey: ['vacationrental', 'inbox', item.id, 'text'],
    queryFn: () => api<{ text: string | null }>(`/api/vacationrental/inbox/${item.id}/text`),
  })
  const action = useMutation({
    mutationFn: ({ url, method = 'POST', body }: { url: string; method?: string; body?: unknown; done: string }) =>
      api(url, { method, json: method === 'POST' ? (body ?? {}) : undefined }),
    onSuccess: async (_, { done }) => {
      await invalidate()
      toast.success(done)
      onClose()
    },
    onError: (err) => toast.error(errorMessage(err)),
  })
  const base = `/api/vacationrental/inbox/${item.id}`

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{item.subject || 'Mail'}</DialogTitle>
          <DialogDescription className="flex flex-wrap items-center gap-2">
            <Badge variant="outline">{importResultLabel[item.result]}</Badge>
            {item.mailType && <span>{mailTypeLabel[item.mailType]}</span>}
            {item.agency && <span>· {item.agency}</span>}
            {item.parsedBy === 'AI' && <span>· per KI gelesen</span>}
          </DialogDescription>
        </DialogHeader>
        {item.resultDetail && <p className="text-sm">{item.resultDetail}</p>}

        {item.result === 'UNASSIGNED_UNIT' && item.propertyName && (
          <div className="space-y-2 rounded-md border p-3">
            <Label htmlFor="ib-unit">
              Einheit für „{item.propertyName}“ ({item.agency})
            </Label>
            <div className="flex flex-wrap gap-2">
              <Select value={unitId} onValueChange={setUnitId}>
                <SelectTrigger id="ib-unit" className="w-72">
                  <SelectValue placeholder="Einheit wählen" />
                </SelectTrigger>
                <SelectContent>
                  {editable.map((u) => (
                    <SelectItem key={u.id} value={String(u.id)}>
                      {unitLabel(u)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button
                type="button"
                disabled={!unitId || action.isPending}
                onClick={() => action.mutate({ url: `${base}/assign`, body: { unitId: Number(unitId) }, done: 'Einheit zugeordnet, Mail verarbeitet.' })}
              >
                Zuordnen
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">Logis merkt sich die Zuordnung für weitere Mails dieses Vermittlers.</p>
          </div>
        )}

        <div className="flex flex-wrap gap-2">
          {item.bookingId && item.bookingRef && (
            <Button type="button" size="sm" onClick={() => navigate(`/ferienvermietung/buchungen?q=${encodeURIComponent(item.bookingRef!)}`)}>
              Zur Buchung
            </Button>
          )}
          {RETRYABLE.includes(item.result) && (
            <Button type="button" size="sm" variant="outline" disabled={action.isPending} onClick={() => action.mutate({ url: `${base}/retry`, done: 'Mail erneut verarbeitet.' })}>
              Erneut verarbeiten
            </Button>
          )}
          {item.result !== 'IGNORED' && (
            <Button type="button" size="sm" variant="outline" disabled={action.isPending} onClick={() => action.mutate({ url: `${base}/ignore`, done: 'Mail ignoriert.' })}>
              Ignorieren
            </Button>
          )}
          <ConfirmButton
            question="Mailinhalt samt Gastdaten löschen? An Buchungen angehängte Kopien bleiben."
            disabled={action.isPending}
            onConfirm={() => action.mutate({ url: base, method: 'DELETE', done: 'Mail gelöscht.' })}
          >
            Löschen
          </ConfirmButton>
        </div>

        <pre className="max-h-80 overflow-auto whitespace-pre-wrap rounded-md bg-muted p-3 text-xs" aria-label="Mailtext">
          {text?.text ?? 'Wird geladen …'}
        </pre>
      </DialogContent>
    </Dialog>
  )
}
