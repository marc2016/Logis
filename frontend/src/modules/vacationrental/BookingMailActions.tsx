import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { MailInboxItem } from '@/app/types'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { unitLabel, useRentalUnits } from './api'

/** Inbox actions for booking mails: assign the agency's property name to a unit, open the booking. */
export function BookingMailActions({ item, onDone }: { item: MailInboxItem; onDone: () => void }) {
  const navigate = useNavigate()
  const { data: units = [] } = useRentalUnits()
  const editable = units.filter((u) => !u.archived && u.myRole !== 'VIEWER')
  const [unitId, setUnitId] = useState('')
  const assign = useMutation({
    mutationFn: () => api(`/api/vacationrental/mail/${item.id}/assign`, { method: 'POST', json: { unitId: Number(unitId) } }),
    onSuccess: () => {
      toast.success('Einheit zugeordnet, Mail verarbeitet.')
      onDone()
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  return (
    <>
      {item.result === 'UNASSIGNED_UNIT' && item.subjectName && (
        <div className="space-y-2 rounded-md border p-3">
          <Label htmlFor="ib-unit">
            Einheit für „{item.subjectName}“ ({item.source})
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
            <Button type="button" disabled={!unitId || assign.isPending} onClick={() => assign.mutate()}>
              Zuordnen
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">Logis merkt sich die Zuordnung für weitere Mails dieses Vermittlers.</p>
        </div>
      )}
      {item.linkId && item.reference && (
        <div>
          <Button type="button" size="sm" onClick={() => navigate(`/ferienvermietung/buchungen?q=${encodeURIComponent(item.reference!)}`)}>
            Zur Buchung
          </Button>
        </div>
      )}
    </>
  )
}
