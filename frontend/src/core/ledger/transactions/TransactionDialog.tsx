import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { CategorySelect } from '@/components/CategorySelect'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { isNegative, parseAmount, toInputValue } from '@/lib/money'
import { useInvalidateLedger, type Account, type Transaction } from '../api'
import { todayIso } from './format'

type Direction = 'EXPENSE' | 'INCOME'

interface Props {
  /** Bestehende Transaktion oder null fuer eine neue. */
  transaction: Transaction | null
  /** Konten, in denen der Benutzer buchen darf. */
  accounts: Account[]
  defaultAccountId?: number
  onClose: () => void
}

export function TransactionDialog({ transaction, accounts, defaultAccountId, onClose }: Props) {
  const isNew = transaction === null
  const [direction, setDirection] = useState<Direction>(transaction && !isNegative(transaction.amount) ? 'INCOME' : 'EXPENSE')
  const [amount, setAmount] = useState(transaction ? toInputValue(transaction.amount) : '')
  const [accountId, setAccountId] = useState<string>(String(transaction?.accountId ?? defaultAccountId ?? accounts[0]?.id ?? ''))
  const [bookingDate, setBookingDate] = useState(transaction?.bookingDate ?? todayIso())
  const [categoryId, setCategoryId] = useState<number | null>(transaction?.categoryId ?? null)
  const [counterparty, setCounterparty] = useState(transaction?.counterparty ?? '')
  const [purpose, setPurpose] = useState(transaction?.purpose ?? '')
  const [note, setNote] = useState(transaction?.note ?? '')
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateLedger()

  const save = useMutation({
    mutationFn: (signedAmount: string) => {
      const body = {
        accountId: Number(accountId),
        bookingDate,
        amount: signedAmount,
        categoryId,
        counterparty,
        purpose,
        note,
        version: transaction?.version,
      }
      return isNew
        ? api<Transaction>('/api/transactions', { json: body })
        : api<Transaction>(`/api/transactions/${transaction.id}`, { method: 'PUT', json: body })
    },
    onSuccess: async () => {
      await invalidate()
      toast.success(isNew ? 'Transaktion erfasst.' : 'Transaktion gespeichert.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const parsed = parseAmount(amount)
    if (parsed === null || isNegative(parsed) || /^0+\.00$/.test(parsed)) {
      setError('Bitte einen gültigen Betrag größer 0 eingeben, z. B. 84,90.')
      return
    }
    save.mutate(direction === 'EXPENSE' ? `-${parsed}` : parsed)
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Transaktion erfassen' : 'Transaktion bearbeiten'}</DialogTitle>
          </DialogHeader>
          <ToggleGroup
            type="single"
            variant="outline"
            value={direction}
            onValueChange={(v) => v && setDirection(v as Direction)}
            className="w-full"
            aria-label="Art der Transaktion"
          >
            <ToggleGroupItem value="EXPENSE" className="flex-1">
              Ausgabe
            </ToggleGroupItem>
            <ToggleGroupItem value="INCOME" className="flex-1">
              Einnahme
            </ToggleGroupItem>
          </ToggleGroup>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="tx-amount">Betrag (€)</Label>
              <Input id="tx-amount" inputMode="decimal" placeholder="0,00" value={amount} onChange={(e) => setAmount(e.target.value)} required autoFocus />
            </div>
            <div className="space-y-2">
              <Label htmlFor="tx-date">Datum</Label>
              <Input id="tx-date" type="date" value={bookingDate} onChange={(e) => setBookingDate(e.target.value)} required />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="tx-account">Konto</Label>
            <Select value={accountId} onValueChange={setAccountId}>
              <SelectTrigger id="tx-account" className="w-full">
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
          </div>
          <div className="space-y-2">
            <Label htmlFor="tx-category">Kategorie</Label>
            <CategorySelect id="tx-category" value={categoryId} onChange={setCategoryId} currentName={transaction?.categoryName} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="tx-counterparty">Gegenpartei</Label>
            <Input id="tx-counterparty" value={counterparty} onChange={(e) => setCounterparty(e.target.value)} placeholder="z. B. Edeka" />
          </div>
          <div className="space-y-2">
            <Label htmlFor="tx-purpose">Verwendungszweck</Label>
            <Input id="tx-purpose" value={purpose} onChange={(e) => setPurpose(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="tx-note">Notiz</Label>
            <Textarea id="tx-note" value={note} onChange={(e) => setNote(e.target.value)} rows={2} />
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
