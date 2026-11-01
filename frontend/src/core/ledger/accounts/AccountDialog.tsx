import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { parseAmount, toInputValue } from '@/lib/money'
import { accountKindLabel, useInvalidateLedger, useLegalEntities, type Account, type AccountKind } from '../api'

/** Create an account (account = null) or edit it (owners only). */
export function AccountDialog({ account, onClose }: { account: Account | null; onClose: () => void }) {
  const isNew = account === null
  const { data: entities = [] } = useLegalEntities()
  const [legalEntityId, setLegalEntityId] = useState<string>('')
  const [name, setName] = useState(account?.name ?? '')
  const [kind, setKind] = useState<AccountKind>(account?.kind ?? 'CHECKING')
  const [iban, setIban] = useState(account?.iban ?? '')
  const [opening, setOpening] = useState(account ? (account.openingBalance.startsWith('-') ? '-' : '') + toInputValue(account.openingBalance) : '0,00')
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateLedger()

  const entityId = legalEntityId || (entities[0] ? String(entities[0].id) : '')

  const save = useMutation({
    mutationFn: (openingBalance: string) =>
      isNew
        ? api<Account>('/api/accounts', { json: { legalEntityId: Number(entityId), name, kind, iban, openingBalance } })
        : api<Account>(`/api/accounts/${account.id}`, {
            method: 'PUT',
            json: { name, kind, iban, openingBalance, archived: account.archived },
          }),
    onSuccess: async () => {
      await invalidate()
      toast.success(isNew ? 'Konto angelegt.' : 'Änderungen gespeichert.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const openingBalance = parseAmount(opening || '0')
    if (openingBalance === null) {
      setError('Bitte einen gültigen Anfangssaldo eingeben, z. B. 1.250,00.')
      return
    }
    save.mutate(openingBalance)
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Konto anlegen' : 'Konto bearbeiten'}</DialogTitle>
          </DialogHeader>
          {isNew && (
            <div className="space-y-2">
              <Label htmlFor="acc-entity">Eigentümer</Label>
              <Select value={entityId} onValueChange={setLegalEntityId}>
                <SelectTrigger id="acc-entity" className="w-full">
                  <SelectValue placeholder="Eigentümer wählen" />
                </SelectTrigger>
                <SelectContent>
                  {entities.map((e) => (
                    <SelectItem key={e.id} value={String(e.id)}>
                      {e.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="acc-name">Name</Label>
            <Input id="acc-name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          </div>
          <div className="space-y-2">
            <Label htmlFor="acc-kind">Art</Label>
            <Select value={kind} onValueChange={(v) => setKind(v as AccountKind)}>
              <SelectTrigger id="acc-kind" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(accountKindLabel) as AccountKind[]).map((k) => (
                  <SelectItem key={k} value={k}>
                    {accountKindLabel[k]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="acc-iban">IBAN (optional)</Label>
            <Input id="acc-iban" value={iban} onChange={(e) => setIban(e.target.value)} placeholder="DE.." />
          </div>
          <div className="space-y-2">
            <Label htmlFor="acc-opening">Anfangssaldo (€)</Label>
            <Input id="acc-opening" inputMode="decimal" value={opening} onChange={(e) => setOpening(e.target.value)} />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={save.isPending || (isNew && !entityId)}>
              Speichern
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
