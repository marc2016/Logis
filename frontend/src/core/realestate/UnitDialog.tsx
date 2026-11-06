import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { UsageSelect } from './UsageSelect'
import { useInvalidateProperties, type Property, type Unit, type UnitUsage } from './api'

/** Add a unit to a property (unit = null) or edit it (editors of the property). */
export function UnitDialog({ propertyId, unit, onClose }: { propertyId: number; unit: Unit | null; onClose: () => void }) {
  const isNew = unit === null
  const [name, setName] = useState(unit?.name ?? '')
  const [usage, setUsage] = useState<UnitUsage>(unit?.usage ?? 'VACATION_RENTAL')
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateProperties()

  const save = useMutation({
    mutationFn: () =>
      isNew
        ? api<Property>(`/api/properties/${propertyId}/units`, { json: { name, usage } })
        : api<Property>(`/api/units/${unit.id}`, { method: 'PUT', json: { name, usage } }),
    onSuccess: async () => {
      await invalidate()
      toast.success(isNew ? 'Einheit angelegt.' : 'Änderungen gespeichert.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    save.mutate()
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Einheit hinzufügen' : 'Einheit bearbeiten'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="unit-name">Name</Label>
            <Input id="unit-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="z. B. Einliegerwohnung" required autoFocus />
          </div>
          <div className="space-y-2">
            <Label htmlFor="unit-usage">Nutzung</Label>
            <UsageSelect id="unit-usage" value={usage} onChange={setUsage} />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={save.isPending}>
              Speichern
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
