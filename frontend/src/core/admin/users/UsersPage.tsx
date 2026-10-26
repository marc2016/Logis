import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { MoreHorizontalIcon, PlusIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { api, ApiError } from '@/lib/api'
import { useCurrentUser } from '@/core/auth/session'

type Role = 'ADMIN' | 'USER'

export interface AdminUser {
  id: number
  username: string
  displayName: string
  role: Role
  enabled: boolean
  totpEnabled: boolean
  lastLoginAt: string | null
  createdAt: string
}

const usersKey = ['admin-users'] as const
const roleLabel: Record<Role, string> = { ADMIN: 'Admin', USER: 'Benutzer' }

function message(error: unknown) {
  return error instanceof ApiError ? error.message : 'Ein unerwarteter Fehler ist aufgetreten.'
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' }) : '–'
}

function RoleSelect({ value, onChange }: { value: Role; onChange: (r: Role) => void }) {
  return (
    <Select value={value} onValueChange={(v) => onChange(v as Role)}>
      <SelectTrigger id="role" className="w-full">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="USER">Benutzer</SelectItem>
        <SelectItem value="ADMIN">Admin</SelectItem>
      </SelectContent>
    </Select>
  )
}

function CreateUserDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const queryClient = useQueryClient()
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [role, setRole] = useState<Role>('USER')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: () => api<AdminUser>('/api/admin/users', { json: { username, displayName, role, password } }),
    onSuccess: (user) => {
      queryClient.invalidateQueries({ queryKey: usersKey })
      toast.success(`Benutzer „${user.username}“ angelegt.`)
      setUsername('')
      setDisplayName('')
      setRole('USER')
      setPassword('')
      onOpenChange(false)
    },
    onError: (err) => setError(message(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    create.mutate()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Benutzer anlegen</DialogTitle>
            <DialogDescription>
              Der Benutzer muss beim ersten Login ein eigenes Passwort setzen und die Zwei-Faktor-Anmeldung einrichten.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="new-username">Benutzername</Label>
            <Input id="new-username" value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus />
          </div>
          <div className="space-y-2">
            <Label htmlFor="new-display-name">Anzeigename</Label>
            <Input id="new-display-name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="role">Rolle</Label>
            <RoleSelect value={role} onChange={setRole} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="new-start-password">Startpasswort</Label>
            <Input id="new-start-password" type="text" autoComplete="off" minLength={12} value={password} onChange={(e) => setPassword(e.target.value)} required />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={create.isPending}>
              Anlegen
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function EditUserDialog({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState(user.displayName)
  const [role, setRole] = useState<Role>(user.role)
  const [error, setError] = useState<string | null>(null)

  const update = useMutation({
    mutationFn: () => api<AdminUser>(`/api/admin/users/${user.id}`, { method: 'PATCH', json: { displayName, role } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: usersKey })
      toast.success('Änderungen gespeichert.')
      onClose()
    },
    onError: (err) => setError(message(err)),
  })

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            setError(null)
            update.mutate()
          }}
          className="space-y-4"
        >
          <DialogHeader>
            <DialogTitle>{user.username} bearbeiten</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="edit-display-name">Anzeigename</Label>
            <Input id="edit-display-name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
          </div>
          <div className="space-y-2">
            <Label htmlFor="role">Rolle</Label>
            <RoleSelect value={role} onChange={setRole} />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={update.isPending}>
              Speichern
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function ResetPasswordDialog({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const reset = useMutation({
    mutationFn: () => api(`/api/admin/users/${user.id}/reset-password`, { json: { password } }),
    onSuccess: () => {
      toast.success(`Passwort von „${user.username}“ zurückgesetzt.`)
      onClose()
    },
    onError: (err) => setError(message(err)),
  })

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            setError(null)
            reset.mutate()
          }}
          className="space-y-4"
        >
          <DialogHeader>
            <DialogTitle>Passwort zurücksetzen</DialogTitle>
            <DialogDescription>
              {user.username} wird abgemeldet und muss nach dem nächsten Login ein eigenes Passwort festlegen.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="reset-password">Neues Startpasswort</Label>
            <Input id="reset-password" type="text" autoComplete="off" minLength={12} value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={reset.isPending}>
              Zurücksetzen
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface Confirmation {
  title: string
  description: string
  confirmLabel: string
  run: () => Promise<unknown>
  success: string
}

function ConfirmDialog({ confirmation, onClose }: { confirmation: Confirmation; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const action = useMutation({
    mutationFn: confirmation.run,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: usersKey })
      toast.success(confirmation.success)
      onClose()
    },
    onError: (err) => setError(message(err)),
  })

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{confirmation.title}</DialogTitle>
          <DialogDescription>{confirmation.description}</DialogDescription>
        </DialogHeader>
        <FormError message={error} />
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Abbrechen
          </Button>
          <Button onClick={() => action.mutate()} disabled={action.isPending}>
            {confirmation.confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function UsersPage() {
  const currentUser = useCurrentUser()
  const { data: users = [], isLoading } = useQuery({
    queryKey: usersKey,
    queryFn: () => api<AdminUser[]>('/api/admin/users'),
  })
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<AdminUser | null>(null)
  const [resetting, setResetting] = useState<AdminUser | null>(null)
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null)

  function toggleEnabled(user: AdminUser) {
    const enable = !user.enabled
    setConfirmation({
      title: enable ? `${user.username} reaktivieren?` : `${user.username} deaktivieren?`,
      description: enable
        ? 'Der Benutzer kann sich danach wieder mit seinen bisherigen Zugangsdaten anmelden.'
        : 'Der Benutzer wird sofort abgemeldet und kann sich nicht mehr anmelden.',
      confirmLabel: enable ? 'Reaktivieren' : 'Deaktivieren',
      run: () => api(`/api/admin/users/${user.id}`, { method: 'PATCH', json: { enabled: enable } }),
      success: enable ? 'Benutzer reaktiviert.' : 'Benutzer deaktiviert.',
    })
  }

  function resetTotp(user: AdminUser) {
    setConfirmation({
      title: 'Zwei-Faktor-Anmeldung zurücksetzen?',
      description: `${user.username} wird abgemeldet, muss die Zwei-Faktor-Anmeldung neu einrichten und alle Wiederherstellungscodes werden ungültig.`,
      confirmLabel: '2FA zurücksetzen',
      run: () => api(`/api/admin/users/${user.id}/reset-totp`, { method: 'POST' }),
      success: 'Zwei-Faktor-Anmeldung zurückgesetzt.',
    })
  }

  return (
    <>
      <PageHeader
        title="Benutzer"
        description="Benutzer anlegen und verwalten. Eine Selbstregistrierung gibt es nicht."
        actions={
          <Button onClick={() => setCreating(true)}>
            <PlusIcon />
            Benutzer anlegen
          </Button>
        }
      />
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Benutzer</TableHead>
              <TableHead>Rolle</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>2FA</TableHead>
              <TableHead className="hidden md:table-cell">Letzte Anmeldung</TableHead>
              <TableHead className="w-12">
                <span className="sr-only">Aktionen</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={6} className="text-muted-foreground">
                  Wird geladen …
                </TableCell>
              </TableRow>
            )}
            {users.map((user) => (
              <TableRow key={user.id}>
                <TableCell>
                  <div className="font-medium">{user.displayName}</div>
                  <div className="text-xs text-muted-foreground">{user.username}</div>
                </TableCell>
                <TableCell>
                  <Badge variant={user.role === 'ADMIN' ? 'default' : 'secondary'}>{roleLabel[user.role]}</Badge>
                </TableCell>
                <TableCell>
                  <Badge variant={user.enabled ? 'outline' : 'destructive'}>{user.enabled ? 'Aktiv' : 'Deaktiviert'}</Badge>
                </TableCell>
                <TableCell>
                  <Badge variant="outline">{user.totpEnabled ? 'Eingerichtet' : 'Ausstehend'}</Badge>
                </TableCell>
                <TableCell className="hidden md:table-cell">{formatDate(user.lastLoginAt)}</TableCell>
                <TableCell>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon" aria-label={`Aktionen für ${user.username}`}>
                        <MoreHorizontalIcon />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onSelect={() => setEditing(user)}>Bearbeiten</DropdownMenuItem>
                      <DropdownMenuItem onSelect={() => setResetting(user)}>Passwort zurücksetzen</DropdownMenuItem>
                      <DropdownMenuItem onSelect={() => resetTotp(user)}>2FA zurücksetzen</DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem
                        disabled={user.id === currentUser.id}
                        variant={user.enabled ? 'destructive' : 'default'}
                        onSelect={() => toggleEnabled(user)}
                      >
                        {user.enabled ? 'Deaktivieren' : 'Reaktivieren'}
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <CreateUserDialog open={creating} onOpenChange={setCreating} />
      {editing && <EditUserDialog key={editing.id} user={editing} onClose={() => setEditing(null)} />}
      {resetting && <ResetPasswordDialog user={resetting} onClose={() => setResetting(null)} />}
      {confirmation && <ConfirmDialog confirmation={confirmation} onClose={() => setConfirmation(null)} />}
    </>
  )
}
