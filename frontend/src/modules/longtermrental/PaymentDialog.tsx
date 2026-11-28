import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DateField } from '@/components/DateField'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { parseAmount, toInputValue } from '@/lib/money'
import { useAccounts } from '@/core/ledger/api'
import { todayIso } from '@/core/ledger/transactions/format'
import { usePreference } from '@/core/preferences/usePreference'
import { TENANCIES, useInvalidateAfterPayment, type RentAccount, type RentPayment } from './api'

/** The account of the last recorded payment, per user (design D6 of rent-payments). */
const ACCOUNT_PREFERENCE = 'longtermrental.paymentAccount'

interface Props {
  tenancyId: number
  /** The payment to change, or null to record one. */
  payment: RentPayment | null
  onClose: () => void
}

/** „Zahlung erfassen“: date, amount, account and note; the transaction in the account is created with it. */
export function PaymentDialog({ tenancyId, payment, onClose }: Props) {
  const isNew = payment === null
  const { data: accounts = [] } = useAccounts()
  const writable = accounts.filter((a) => !a.archived && a.myRole !== 'VIEWER')
  const [remembered, remember] = usePreference<number | null>(ACCOUNT_PREFERENCE, null)
  const [date, setDate] = useState(payment?.date ?? todayIso())
  const [amount, setAmount] = useState(payment ? toInputValue(payment.amount) : '')
  const [chosen, setChosen] = useState(payment?.accountId ? String(payment.accountId) : '')
  const [note, setNote] = useState(payment?.note ?? '')
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateAfterPayment()
  const fallback = writable.find((a) => a.id === remembered) ?? (writable.length === 1 ? writable[0] : undefined)
  const accountId = chosen || (fallback ? String(fallback.id) : '')

  const save = useMutation({
    mutationFn: (body: unknown) =>
      isNew
        ? api<RentAccount>(`${TENANCIES}/${tenancyId}/payments`, { json: body })
        : api<RentAccount>(`${TENANCIES}/${tenancyId}/payments/${payment.id}`, { method: 'PUT', json: body }),
    onSuccess: async () => {
      remember(Number(accountId))
      await invalidate()
      toast.success(isNew ? 'Zahlung erfasst.' : 'Zahlung gespeichert.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const parsed = parseAmount(amount)
    if (parsed === null || parsed.startsWith('-') || /^0+(\.0+)?$/.test(parsed)) {
      setError('Bitte einen Betrag größer als 0 eingeben, z. B. 800,00.')
      return
    }
    save.mutate({ date, amount: parsed, accountId: Number(accountId), note: note.trim() || null, version: payment?.version })
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Zahlung erfassen' : 'Zahlung bearbeiten'}</DialogTitle>
            <DialogDescription>Die Zahlung wird auch als Transaktion im gewählten Konto gebucht und begleicht den ältesten offenen Monat.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="pay-date">Datum</Label>
              <DateField id="pay-date" label="Datum" value={date} onChange={setDate} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="pay-amount">Betrag (€)</Label>
              <Input id="pay-amount" inputMode="decimal" placeholder="0,00" value={amount} onChange={(e) => setAmount(e.target.value)} required />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="pay-account">Konto</Label>
            <Select value={accountId} onValueChange={setChosen}>
              <SelectTrigger id="pay-account" className="w-full">
                <SelectValue placeholder={writable.length ? 'Konto wählen' : 'Kein Konto, in das du buchen darfst'} />
              </SelectTrigger>
              <SelectContent>
                {writable.map((a) => (
                  <SelectItem key={a.id} value={String(a.id)}>
                    {a.name} · {a.legalEntity.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="pay-note">Notiz</Label>
            <Input id="pay-note" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={save.isPending || !accountId}>
              Speichern
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
