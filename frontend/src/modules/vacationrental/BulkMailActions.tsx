import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { BulkMailRun, MailInboxItem } from '@/app/types'
import { api } from '@/lib/api'
import { SkipError } from '@/lib/bulk'
import { unitLabel, useRentalUnits } from './api'

/** „Einheit zuordnen“ for several booking mails that wait for a unit. */
export function BulkMailActions({ items, busy, run }: { items: MailInboxItem[]; busy: boolean; run: BulkMailRun }) {
  const { data: units = [] } = useRentalUnits()
  const editable = units.filter((u) => !u.archived && u.myRole !== 'VIEWER')
  const [open, setOpen] = useState(false)
  const [unitId, setUnitId] = useState('')
  const waiting = items.filter((m) => m.result === 'UNASSIGNED_UNIT' && m.subjectName)
  if (waiting.length === 0) return null

  return (
    <>
      <Button size="sm" variant="outline" disabled={busy} onClick={() => setOpen(true)}>
        Einheit zuordnen
      </Button>
      {open && (
        <Dialog open onOpenChange={(o) => !o && setOpen(false)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Einheit zuordnen</DialogTitle>
              <DialogDescription>
                {waiting.length} {waiting.length === 1 ? 'Mail wartet' : 'Mails warten'} auf eine Einheit. Logis merkt sich die Zuordnung für weitere Mails.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-2">
              <Label htmlFor="bulk-unit">Einheit</Label>
              <Select value={unitId} onValueChange={setUnitId}>
                <SelectTrigger id="bulk-unit" className="w-full">
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
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Abbrechen
              </Button>
              <Button
                type="button"
                disabled={!unitId}
                onClick={() => {
                  setOpen(false)
                  run(items, (m) => {
                    if (m.result !== 'UNASSIGNED_UNIT' || !m.subjectName) throw new SkipError('Die Mail wartet nicht auf eine Einheit')
                    // The server also processes other waiting mails of the same agency and name again.
                    return api(`/api/vacationrental/mail/${m.id}/assign`, { method: 'POST', json: { unitId: Number(unitId) } })
                  }, 'zugeordnet')
                }}
              >
                Zuordnen
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </>
  )
}
