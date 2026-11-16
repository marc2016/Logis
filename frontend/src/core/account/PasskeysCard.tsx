import { useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { KeyRoundIcon, PencilIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ConfirmButton } from '@/components/ConfirmButton'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { isPasskeyCancel, registerPasskey, type Passkey } from '@/lib/passkeys'
import { formatDate } from '@/core/ledger/transactions/format'

const passkeysKey = ['account', 'passkeys'] as const

/** Adding a passkey: current password and a name, then the prompt of the device. */
function AddPasskeyDialog({ onClose, onAdded }: { onClose: () => void; onAdded: () => void }) {
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await registerPasskey(password, name.trim())
      toast.success('Passkey hinzugefügt.')
      onAdded()
    } catch (err) {
      if (!isPasskeyCancel(err)) setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Passkey hinzufügen</DialogTitle>
            <DialogDescription>Bestätige dein Passwort; danach fragt dein Gerät nach Face ID, Touch ID oder PIN.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="pk-password">Aktuelles Passwort</Label>
            <Input id="pk-password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus />
          </div>
          <div className="space-y-2">
            <Label htmlFor="pk-name">Name</Label>
            <Input id="pk-name" maxLength={64} placeholder="z. B. MacBook" value={name} onChange={(e) => setName(e.target.value)} required />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={busy}>
              Hinzufügen
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function RenameDialog({ passkey, onClose, onSaved }: { passkey: Passkey; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(passkey.name)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    try {
      await api(`/api/account/passkeys/${passkey.id}`, { method: 'PUT', json: { name: name.trim() } })
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Passkey umbenennen</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="pk-rename">Name</Label>
            <Input id="pk-rename" maxLength={64} value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit">Speichern</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** One's own passkeys: list, add, rename, delete. Only shown when passkeys are configured. */
export function PasskeysCard() {
  const queryClient = useQueryClient()
  const { data: passkeys = [] } = useQuery({ queryKey: passkeysKey, queryFn: () => api<Passkey[]>('/api/account/passkeys') })
  const [adding, setAdding] = useState(false)
  const [renaming, setRenaming] = useState<Passkey | null>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: passkeysKey })

  async function remove(p: Passkey) {
    try {
      await api(`/api/account/passkeys/${p.id}`, { method: 'DELETE' })
      toast.success('Passkey gelöscht.')
      await refresh()
    } catch (err) {
      toast.error(errorMessage(err))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Passkeys</CardTitle>
        <CardDescription>
          Anmelden ohne Passwort und Code, mit Face ID, Touch ID oder der PIN deines Geräts. Passkeys gelten nur unter dieser Adresse.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {passkeys.length > 0 && (
          <ul className="divide-y rounded-md border text-sm" aria-label="Meine Passkeys">
            {passkeys.map((p) => (
              <li key={p.id} className="flex flex-wrap items-center gap-2 px-3 py-2">
                <KeyRoundIcon className="size-4 text-muted-foreground" />
                <span className="min-w-0 flex-1">
                  <span className="font-medium">{p.name}</span>
                  <span className="block text-xs text-muted-foreground">
                    Erstellt am {formatDate(p.createdAt.slice(0, 10))}
                    {' · '}
                    {p.lastUsedAt ? `Zuletzt verwendet ${formatDate(p.lastUsedAt.slice(0, 10))}` : 'Noch nicht verwendet'}
                  </span>
                </span>
                <Button variant="ghost" size="icon" aria-label={`${p.name} umbenennen`} onClick={() => setRenaming(p)}>
                  <PencilIcon />
                </Button>
                <ConfirmButton question={`„${p.name}“ löschen?`} onConfirm={() => remove(p)}>
                  Löschen
                </ConfirmButton>
              </li>
            ))}
          </ul>
        )}
        <Button variant="outline" onClick={() => setAdding(true)}>
          <KeyRoundIcon />
          Passkey hinzufügen
        </Button>
      </CardContent>
      {adding && (
        <AddPasskeyDialog
          onClose={() => setAdding(false)}
          onAdded={() => {
            setAdding(false)
            void refresh()
          }}
        />
      )}
      {renaming && (
        <RenameDialog
          passkey={renaming}
          onClose={() => setRenaming(null)}
          onSaved={() => {
            setRenaming(null)
            void refresh()
          }}
        />
      )}
    </Card>
  )
}
