import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { isNegative, parseAmount, toInputValue } from '@/lib/money'
import { useInvalidateLedger, type Account, type Transaction } from '../api'
import { todayIso } from './format'

interface Props {
  /** Eine Seite einer bestehenden Umbuchung oder null fuer eine neue. */
  transaction: Transaction | null
  accounts: Account[]
  defaultFromAccountId?: number
  onClose: () => void
}

function AccountPicker({ id, value, onChange, accounts }: { id: string; value: string; onChange: (v: string) => void; accounts: Account[] }) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger id={id} className="w-full">
        <SelectValue placeholder="Konto wählen" />
      </SelectTrigger>
      <SelectContent>
        {accounts.map((a) => (
          <SelectItem key={a.id} value={String(a.id)}>
            {a.name} – {a.legalEntity.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

export function TransferDialog({ transaction, accounts, defaultFromAccountId, onClose }: Props) {
  const isNew = transaction === null
  const outgoing = transaction ? isNegative(transaction.amount) : true
  const initialFrom = transaction ? (outgoing ? transaction.accountId : transaction.counterAccountId) : defaultFromAccountId
  const initialTo = transaction ? (outgoing ? transaction.counterAccountId : transaction.accountId) : undefined
  const [from, setFrom] = useState(initialFrom ? String(initialFrom) : '')
  const [to, setTo] = useState(initialTo ? String(initialTo) : '')
  const [amount, setAmount] = useState(transaction ? toInputValue(transaction.amount) : '')
  const [bookingDate, setBookingDate] = useState(transaction?.bookingDate ?? todayIso())
  const [note, setNote] = useState(transaction?.note ?? '')
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateLedger()

  const save = useMutation({
    mutationFn: (value: string) => {
      const body = {
        fromAccountId: Number(from),
        toAccountId: Number(to),
        bookingDate,
        amount: value,
        note,
        version: transaction?.version,
      }
      return isNew
        ? api('/api/transfers', { json: body })
        : api(`/api/transfers/${transaction.transferId}`, { method: 'PUT', json: body })
    },
    onSuccess: async () => {
      await invalidate()
      toast.success(isNew ? 'Umbuchung erfasst.' : 'Umbuchung gespeichert.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const parsed = parseAmount(amount)
    if (parsed === null || isNegative(parsed) || /^0+\.00$/.test(parsed)) {
      setError('Bitte einen gültigen Betrag größer 0 eingeben.')
      return
    }
    if (from === to) {
      setError('Quell- und Zielkonto müssen verschieden sein.')
      return
    }
    save.mutate(parsed)
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Umbuchung' : 'Umbuchung bearbeiten'}</DialogTitle>
            <DialogDescription>Geld zwischen eigenen Konten verschieben. Zählt nicht als Einnahme oder Ausgabe.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="tr-from">Von</Label>
            <AccountPicker id="tr-from" value={from} onChange={setFrom} accounts={accounts} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="tr-to">Nach</Label>
            <AccountPicker id="tr-to" value={to} onChange={setTo} accounts={accounts} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="tr-amount">Betrag (€)</Label>
              <Input id="tr-amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="tr-date">Datum</Label>
              <Input id="tr-date" type="date" value={bookingDate} onChange={(e) => setBookingDate(e.target.value)} required />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="tr-note">Notiz</Label>
            <Textarea id="tr-note" value={note} onChange={(e) => setNote(e.target.value)} rows={2} />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={save.isPending || !from || !to}>
              Speichern
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
