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
import { formatDate } from '@/core/ledger/transactions/format'
import { TENANCIES, useInvalidateTenancies, type Rent, type Tenancy } from './api'
import { RentFields } from './forms'
import { parseRent, rentDraft } from './rentDraft'

interface Props {
  tenancy: Tenancy
  /** The entry to correct, or null for „Miete ändern“: a new entry valid from a later date. */
  term: Rent | null
  onClose: () => void
}

/** „Miete ändern“ adds an entry to the rent history; an existing entry can be corrected. */
export function RentDialog({ tenancy, term, onClose }: Props) {
  const isNew = term === null
  const first = term !== null && tenancy.rentTerms[0]?.id === term.id
  const [validFrom, setValidFrom] = useState(term?.validFrom ?? '')
  // A change starts from the latest rent, so only what changes has to be typed.
  const [rent, setRent] = useState(rentDraft(term ?? tenancy.rentTerms.at(-1)))
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateTenancies()

  const save = useMutation({
    mutationFn: (body: unknown) =>
      isNew
        ? api<Tenancy>(`${TENANCIES}/${tenancy.id}/rent-terms`, { json: body })
        : api<Tenancy>(`${TENANCIES}/${tenancy.id}/rent-terms/${term.id}`, { method: 'PUT', json: body }),
    onSuccess: async () => {
      await invalidate()
      toast.success(isNew ? 'Miete geändert.' : 'Miete gespeichert.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const parsed = parseRent(rent)
    if (typeof parsed === 'string') {
      setError(parsed)
      return
    }
    save.mutate({ ...parsed, validFrom })
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Miete ändern' : 'Miete korrigieren'}</DialogTitle>
            <DialogDescription>
              {isNew
                ? 'Die neue Miete gilt ab dem angegebenen Tag; die bisherige bleibt im Mietverlauf.'
                : first
                  ? `Die erste Miete gilt ab dem Beginn des Mietverhältnisses (${formatDate(term.validFrom)}).`
                  : 'Korrigiert diesen Eintrag des Mietverlaufs.'}
            </DialogDescription>
          </DialogHeader>
          {!first && (
            <div className="space-y-2">
              <Label htmlFor="rt-from">Gültig ab</Label>
              <DateField id="rt-from" label="Gültig ab" value={validFrom} onChange={setValidFrom} required />
            </div>
          )}
          <RentFields idPrefix="rt" value={rent} onChange={setRent} />
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
