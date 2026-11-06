import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckIcon, ChevronsUpDownIcon, UserMinusIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { useCurrentUser } from '@/core/auth/session'
import { ledgerKeys, roleLabel, useShares, useUserSearch, type DirectoryUser, type ShareRole } from '@/core/ledger/api'
import { realestateKeys } from '@/core/realestate/api'

const ROLES: ShareRole[] = ['VIEWER', 'EDITOR', 'OWNER']
const DESCRIPTION = 'Lesen: nur ansehen. Bearbeiten: Buchungen erfassen und ändern. Owner: zusätzlich Konto und Freigaben verwalten.'

function RoleSelect({ value, onChange, roles, id, label }: { value: ShareRole; onChange: (r: ShareRole) => void; roles: ShareRole[]; id?: string; label?: string }) {
  return (
    <Select value={value} onValueChange={(v) => onChange(v as ShareRole)}>
      <SelectTrigger id={id} className="w-36" aria-label={label}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {roles.map((role) => (
          <SelectItem key={role} value={role}>
            {roleLabel[role]}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

function UserPicker({ value, onChange }: { value: DirectoryUser | null; onChange: (u: DirectoryUser) => void }) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const { data: results = [], isFetching } = useUserSearch(query)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button id="share-user" variant="outline" role="combobox" aria-expanded={open} className="w-full justify-between font-normal">
          {value ? `${value.displayName} (${value.username})` : 'Benutzer suchen …'}
          <ChevronsUpDownIcon className="opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-(--radix-popover-trigger-width) p-0" align="start">
        <Command shouldFilter={false}>
          <CommandInput placeholder="Name oder Benutzername" value={query} onValueChange={setQuery} />
          <CommandList>
            <CommandEmpty>{query.trim().length < 2 ? 'Mindestens 2 Zeichen eingeben' : isFetching ? 'Suche …' : 'Niemand gefunden'}</CommandEmpty>
            <CommandGroup>
              {results.map((user) => (
                <CommandItem
                  key={user.id}
                  value={String(user.id)}
                  onSelect={() => {
                    onChange(user)
                    setOpen(false)
                  }}
                >
                  <CheckIcon className={value?.id === user.id ? 'opacity-100' : 'opacity-0'} />
                  {user.displayName} <span className="text-muted-foreground">({user.username})</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}

interface Props {
  objectType: string
  objectId: number
  objectName: string
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Roles that may be granted; units only allow reading and editing. */
  roles?: ShareRole[]
  description?: string
}

/** Shows an object's shares and - as owner - manages them. For all shareable types. */
export function ShareDialog({ objectType, objectId, objectName, open, onOpenChange, roles = ROLES, description = DESCRIPTION }: Props) {
  const me = useCurrentUser()
  const queryClient = useQueryClient()
  const { data } = useShares(objectType, objectId, open)
  const [target, setTarget] = useState<DirectoryUser | null>(null)
  const [role, setRole] = useState<ShareRole>('VIEWER')
  const [error, setError] = useState<string | null>(null)
  const canManage = data?.canManage === true
  const base = `/api/shares/${objectType}/${objectId}`

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ledgerKeys.shares(objectType, objectId) }),
      queryClient.invalidateQueries({ queryKey: ledgerKeys.accounts }),
      queryClient.invalidateQueries({ queryKey: realestateKeys.properties }),
    ])

  const mutation = useMutation({
    mutationFn: (fn: () => Promise<unknown>) => fn(),
    onMutate: () => setError(null),
    onSuccess: refresh,
    onError: (err) => setError(errorMessage(err)),
  })

  function invite() {
    if (!target) return
    mutation.mutate(() => api(base, { json: { userId: target.id, role } }), {
      onSuccess: () => {
        toast.success(`${target.displayName} wurde eingeladen.`)
        setTarget(null)
      },
    })
  }

  function leave() {
    mutation.mutate(() => api(`${base}/${me.id}`, { method: 'DELETE' }), {
      onSuccess: () => {
        toast.success('Freigabe aufgegeben.')
        onOpenChange(false)
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Freigaben: {objectName}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>

        <ul className="divide-y rounded-lg border" aria-label="Freigaben">
          {data?.shares.map((share) => (
            <li key={share.userId} className="flex items-center gap-3 p-3">
              <div className="min-w-0 flex-1">
                <div className="truncate font-medium">
                  {share.displayName} {share.userId === me.id && <span className="text-muted-foreground">(du)</span>}
                </div>
                <div className="truncate text-xs text-muted-foreground">{share.username}</div>
              </div>
              {canManage ? (
                <>
                  <RoleSelect
                    value={share.role}
                    roles={roles}
                    label={`Rolle von ${share.displayName}`}
                    onChange={(r) => mutation.mutate(() => api(`${base}/${share.userId}`, { method: 'PATCH', json: { role: r } }))}
                  />
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Freigabe von ${share.displayName} entziehen`}
                    onClick={() => mutation.mutate(() => api(`${base}/${share.userId}`, { method: 'DELETE' }))}
                  >
                    <UserMinusIcon />
                  </Button>
                </>
              ) : (
                <Badge variant="outline">{roleLabel[share.role]}</Badge>
              )}
            </li>
          ))}
        </ul>

        {canManage && (
          <div className="space-y-2">
            <Label htmlFor="share-user">Benutzer einladen</Label>
            <div className="flex flex-col gap-2 sm:flex-row">
              <div className="flex-1">
                <UserPicker value={target} onChange={setTarget} />
              </div>
              <RoleSelect value={role} onChange={setRole} roles={roles} label="Rolle für Einladung" />
              <Button onClick={invite} disabled={!target || mutation.isPending}>
                Einladen
              </Button>
            </div>
          </div>
        )}

        <FormError message={error} />

        {!canManage && data && (
          <Button variant="outline" onClick={leave} disabled={mutation.isPending}>
            Freigabe aufgeben
          </Button>
        )}
      </DialogContent>
    </Dialog>
  )
}
