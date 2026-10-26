import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { RecoveryCodes } from '@/components/RecoveryCodes'
import { api, ApiError } from '@/lib/api'

function ChangePasswordCard() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (next !== confirm) {
      setError('Die Passwörter stimmen nicht überein.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await api('/api/account/password', { json: { currentPassword: current, newPassword: next } })
      setCurrent('')
      setNext('')
      setConfirm('')
      toast.success('Passwort geändert. Andere Sitzungen wurden beendet.')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Ein unerwarteter Fehler ist aufgetreten.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Passwort ändern</CardTitle>
        <CardDescription>Nach der Änderung werden deine Sitzungen auf anderen Geräten beendet.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="current-password">Aktuelles Passwort</Label>
            <Input id="current-password" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="account-new-password">Neues Passwort</Label>
            <Input id="account-new-password" type="password" autoComplete="new-password" minLength={12} value={next} onChange={(e) => setNext(e.target.value)} required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="account-confirm-password">Neues Passwort wiederholen</Label>
            <Input id="account-confirm-password" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
          </div>
          <FormError message={error} />
          <Button type="submit" disabled={busy}>
            Passwort ändern
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

function RecoveryCodesCard() {
  const [codes, setCodes] = useState<string[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function regenerate() {
    setError(null)
    try {
      const result = await api<{ recoveryCodes: string[] }>('/api/account/recovery-codes', { method: 'POST' })
      setCodes(result.recoveryCodes)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Ein unerwarteter Fehler ist aufgetreten.')
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Wiederherstellungscodes</CardTitle>
        <CardDescription>Erzeuge neue Codes, wenn du die alten verloren oder aufgebraucht hast.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {codes ? (
          <>
            <p className="text-sm">Deine neuen Codes – sie werden nur jetzt angezeigt:</p>
            <RecoveryCodes codes={codes} />
          </>
        ) : (
          <Dialog>
            <DialogTrigger asChild>
              <Button variant="outline">Neue Codes erzeugen</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Neue Wiederherstellungscodes erzeugen?</DialogTitle>
                <DialogDescription>Alle bisherigen Codes werden dabei ungültig.</DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <DialogClose asChild>
                  <Button variant="outline">Abbrechen</Button>
                </DialogClose>
                <DialogClose asChild>
                  <Button onClick={regenerate}>Neue Codes erzeugen</Button>
                </DialogClose>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        )}
        <FormError message={error} />
      </CardContent>
    </Card>
  )
}

export function AccountPage() {
  return (
    <>
      <PageHeader title="Mein Konto" />
      <div className="grid max-w-4xl gap-6 lg:grid-cols-2">
        <ChangePasswordCard />
        <RecoveryCodesCard />
      </div>
    </>
  )
}
