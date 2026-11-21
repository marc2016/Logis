import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { DateField } from '@/components/DateField'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { TENANCIES, useInvalidateTenancies, type Tenancy } from './api'

/** „Beenden“: enters the last day of the tenancy; from the next day the unit can be rented again. */
export function EndDialog({ tenancy, onClose }: { tenancy: Tenancy; onClose: () => void }) {
  const [endDate, setEndDate] = useState(tenancy.endDate ?? '')
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateTenancies()

  const save = useMutation({
    mutationFn: () =>
      api<Tenancy>(`${TENANCIES}/${tenancy.id}`, {
        method: 'PUT',
        json: {
          unitId: tenancy.unit.id,
          startDate: tenancy.startDate,
          endDate,
          depositAmount: tenancy.depositAmount,
          depositReceivedOn: tenancy.depositReceivedOn,
          tenants: tenancy.tenants,
          version: tenancy.version,
        },
      }),
    onSuccess: async () => {
      await invalidate()
      toast.success('Mietverhältnis beendet.')
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
      <DialogContent className="sm:max-w-md">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Mietverhältnis beenden</DialogTitle>
            <DialogDescription>Das Ende ist der letzte Tag des Mietverhältnisses.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="te-end">Ende (letzter Tag)</Label>
            <DateField id="te-end" label="Ende (letzter Tag)" value={endDate} onChange={setEndDate} required />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={save.isPending}>
              Beenden
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
