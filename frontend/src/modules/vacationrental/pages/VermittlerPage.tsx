import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { PencilIcon, PlusIcon, XIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ConfirmButton } from '@/components/ConfirmButton'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { rentalKeys, useAgencies, type Agency } from '../api'

const BASE = '/api/vacationrental/agencies'

/** The shared list of agencies: names, mail names, archive, merge and delete. */
export function VermittlerPage() {
  const { data: agencies = [], isLoading } = useAgencies()
  const [dialog, setDialog] = useState<{ agency: Agency | null } | null>(null)
  const editing = dialog?.agency ? agencies.find((a) => a.id === dialog.agency!.id) ?? null : null

  return (
    <>
      <PageHeader
        title="Vermittler"
        description="Gemeinsame Liste für alle Benutzer. Mail-Namen ordnen eingehende Buchungsmails dem Vermittler zu."
        actions={
          <Button onClick={() => setDialog({ agency: null })}>
            <PlusIcon />
            Vermittler
          </Button>
        }
      />
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Mail-Namen</TableHead>
              <TableHead className="text-right">Buchungen</TableHead>
              <TableHead className="w-12">
                <span className="sr-only">Aktionen</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {agencies.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                  {isLoading ? 'Wird geladen …' : 'Noch keine Vermittler.'}
                </TableCell>
              </TableRow>
            ) : (
              agencies.map((a) => (
                <TableRow key={a.id} className={a.archived ? 'text-muted-foreground' : undefined}>
                  <TableCell className="font-medium">
                    {a.name} {a.archived && <Badge variant="secondary">archiviert</Badge>}
                  </TableCell>
                  <TableCell>
                    <span className="inline-flex flex-wrap gap-1">
                      {a.aliases
                        .filter((al) => al.alias !== a.name)
                        .map((al) => (
                          <Badge key={al.id} variant="outline">
                            {al.alias}
                          </Badge>
                        ))}
                    </span>
                  </TableCell>
                  <TableCell className="text-right">{a.bookingCount}</TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon" aria-label={`${a.name} bearbeiten`} onClick={() => setDialog({ agency: a })}>
                      <PencilIcon />
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
      {dialog && !dialog.agency && <CreateDialog onClose={() => setDialog(null)} />}
      {editing && <EditDialog agency={editing} others={agencies.filter((a) => a.id !== editing.id)} onClose={() => setDialog(null)} />}
    </>
  )
}

function useRefresh() {
  const queryClient = useQueryClient()
  // Bookings show agency names too, so everything of the module is refreshed.
  return () => queryClient.invalidateQueries({ queryKey: rentalKeys.all })
}

function CreateDialog({ onClose }: { onClose: () => void }) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const refresh = useRefresh()
  const create = useMutation({
    mutationFn: () => api<Agency>(BASE, { json: { name } }),
    onSuccess: async () => {
      await refresh()
      toast.success('Vermittler angelegt.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    create.mutate()
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Vermittler anlegen</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="ag-new-name">Name</Label>
            <Input id="ag-new-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={64} required />
          </div>
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
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

function EditDialog({ agency, others, onClose }: { agency: Agency; others: Agency[]; onClose: () => void }) {
  const [name, setName] = useState(agency.name)
  const [alias, setAlias] = useState('')
  const [target, setTarget] = useState<string>('')
  const [error, setError] = useState<string | null>(null)
  const refresh = useRefresh()

  const call = useMutation({
    mutationFn: ({ url, method = 'POST', json }: { url: string; method?: string; json?: unknown; done: string; close?: boolean }) =>
      api<Agency | undefined>(url, { method, json }),
    onSuccess: async (_, { done, close }) => {
      await refresh()
      toast.success(done)
      setError(null)
      if (close) onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function rename(e: FormEvent) {
    e.preventDefault()
    call.mutate({ url: `${BASE}/${agency.id}`, method: 'PUT', json: { name, version: agency.version }, done: 'Vermittler umbenannt.' })
  }

  function addAlias(e: FormEvent) {
    e.preventDefault()
    if (!alias.trim()) return
    call.mutate({ url: `${BASE}/${agency.id}/aliases`, json: { alias }, done: 'Mail-Name hinzugefügt.' })
    setAlias('')
  }

  const targetName = others.find((o) => String(o.id) === target)?.name

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Vermittler bearbeiten</DialogTitle>
          <DialogDescription>
            {agency.bookingCount} {agency.bookingCount === 1 ? 'Buchung' : 'Buchungen'}
            {agency.archived && ' · archiviert'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={rename} className="space-y-2">
          <Label htmlFor="ag-name">Name</Label>
          <div className="flex gap-2">
            <Input id="ag-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={64} required />
            <Button type="submit" variant="outline" disabled={call.isPending || name.trim() === agency.name}>
              Umbenennen
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">Der bisherige Name bleibt als Mail-Name erhalten.</p>
        </form>

        <Separator />
        <section className="space-y-2" aria-label="Mail-Namen">
          <h3 className="text-sm font-medium">Mail-Namen</h3>
          <ul className="flex flex-wrap gap-1">
            {agency.aliases.map((al) => (
              <li key={al.id}>
                <Badge variant="outline" className="gap-1">
                  {al.alias}
                  {al.alias !== agency.name && (
                    <button
                      type="button"
                      aria-label={`${al.alias} entfernen`}
                      onClick={() => call.mutate({ url: `${BASE}/${agency.id}/aliases/${al.id}`, method: 'DELETE', done: 'Mail-Name entfernt.' })}
                    >
                      <XIcon className="size-3" />
                    </button>
                  )}
                </Badge>
              </li>
            ))}
          </ul>
          <form onSubmit={addAlias} className="flex gap-2">
            <Input aria-label="Neuer Mail-Name" placeholder="Name, wie er in Mails steht" value={alias} onChange={(e) => setAlias(e.target.value)} />
            <Button type="submit" variant="outline" disabled={call.isPending || !alias.trim()}>
              Hinzufügen
            </Button>
          </form>
        </section>

        {others.length > 0 && (
          <>
            <Separator />
            <section className="space-y-2" aria-label="Zusammenführen">
              <h3 className="text-sm font-medium">Zusammenführen</h3>
              <p className="text-xs text-muted-foreground">
                Buchungen, Zuordnungen und Mail-Namen gehen an den gewählten Vermittler über, dieser hier wird gelöscht.
              </p>
              <div className="flex gap-2">
                <Select value={target} onValueChange={setTarget}>
                  <SelectTrigger aria-label="Zusammenführen mit" className="w-full">
                    <SelectValue placeholder="Vermittler wählen" />
                  </SelectTrigger>
                  <SelectContent>
                    {others.map((o) => (
                      <SelectItem key={o.id} value={String(o.id)}>
                        {o.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <ConfirmButton
                  question={`„${agency.name}“ in „${targetName ?? ''}“ zusammenführen?`}
                  disabled={!target || call.isPending}
                  onConfirm={() =>
                    call.mutate({ url: `${BASE}/${agency.id}/merge`, json: { targetId: Number(target) }, done: 'Vermittler zusammengeführt.', close: true })
                  }
                >
                  Zusammenführen
                </ConfirmButton>
              </div>
            </section>
          </>
        )}

        <FormError message={error} />
        <DialogFooter className="gap-2 sm:justify-between">
          <div className="flex gap-2">
            {agency.archived ? (
              <Button variant="outline" onClick={() => call.mutate({ url: `${BASE}/${agency.id}/restore`, json: { version: agency.version }, done: 'Vermittler wiederhergestellt.' })}>
                Wiederherstellen
              </Button>
            ) : (
              <Button variant="outline" onClick={() => call.mutate({ url: `${BASE}/${agency.id}/archive`, json: { version: agency.version }, done: 'Vermittler archiviert.' })}>
                Archivieren
              </Button>
            )}
            <ConfirmButton
              question={`„${agency.name}“ löschen?`}
              variant="destructive"
              disabled={agency.bookingCount > 0 || call.isPending}
              onConfirm={() => call.mutate({ url: `${BASE}/${agency.id}`, method: 'DELETE', done: 'Vermittler gelöscht.', close: true })}
            >
              Löschen
            </ConfirmButton>
          </div>
          <Button variant="outline" onClick={onClose}>
            Schließen
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
