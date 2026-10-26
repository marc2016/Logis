import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { legalEntityKindLabel, useInvalidateLedger, useLegalEntities, type LegalEntity, type LegalEntityKind } from '../api'

function LegalEntityDialog({ entity, onClose }: { entity: LegalEntity | 'new'; onClose: () => void }) {
  const isNew = entity === 'new'
  const [name, setName] = useState(isNew ? '' : entity.name)
  const [kind, setKind] = useState<LegalEntityKind>(isNew ? 'GBR' : entity.kind)
  const [taxNumber, setTaxNumber] = useState(isNew ? '' : (entity.taxNumber ?? ''))
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateLedger()

  const save = useMutation({
    mutationFn: () =>
      isNew
        ? api('/api/legal-entities', { json: { name, kind, taxNumber } })
        : api(`/api/legal-entities/${entity.id}`, { method: 'PUT', json: { name, kind, taxNumber } }),
    onSuccess: async () => {
      await invalidate()
      toast.success(isNew ? 'Eigentümer angelegt.' : 'Änderungen gespeichert.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    save.mutate()
  }

  const personal = !isNew && entity.personal
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Eigentümer anlegen' : 'Eigentümer bearbeiten'}</DialogTitle>
            <DialogDescription>Eigentümer trennen Konten steuerlich, z. B. privat und eine GbR.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="le-name">Name</Label>
            <Input id="le-name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          </div>
          <div className="space-y-2">
            <Label htmlFor="le-kind">Art</Label>
            <Select value={kind} onValueChange={(v) => setKind(v as LegalEntityKind)} disabled={personal}>
              <SelectTrigger id="le-kind" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(legalEntityKindLabel) as LegalEntityKind[]).map((k) => (
                  <SelectItem key={k} value={k}>
                    {legalEntityKindLabel[k]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="le-tax">Steuernummer (optional)</Label>
            <Input id="le-tax" value={taxNumber} onChange={(e) => setTaxNumber(e.target.value)} />
          </div>
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

export function LegalEntitiesPage() {
  const { data: entities = [], isLoading } = useLegalEntities()
  const [editing, setEditing] = useState<LegalEntity | 'new' | null>(null)
  const invalidate = useInvalidateLedger()

  const remove = useMutation({
    mutationFn: (e: LegalEntity) => api(`/api/legal-entities/${e.id}`, { method: 'DELETE' }),
    onSuccess: async () => {
      await invalidate()
      toast.success('Eigentümer gelöscht.')
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  return (
    <>
      <PageHeader
        title="Eigentümer"
        description="Wem gehören die Konten? Nur du verwaltest die Eigentümer, die du angelegt hast."
        actions={
          <Button onClick={() => setEditing('new')}>
            <PlusIcon />
            Eigentümer anlegen
          </Button>
        }
      />
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Art</TableHead>
              <TableHead className="hidden sm:table-cell">Steuernummer</TableHead>
              <TableHead className="text-right">Konten</TableHead>
              <TableHead className="w-24">
                <span className="sr-only">Aktionen</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={5} className="text-muted-foreground">
                  Wird geladen …
                </TableCell>
              </TableRow>
            )}
            {entities.map((e) => (
              <TableRow key={e.id}>
                <TableCell className="font-medium">
                  {e.name} {e.personal && <Badge variant="secondary">privat</Badge>}
                </TableCell>
                <TableCell>{legalEntityKindLabel[e.kind]}</TableCell>
                <TableCell className="hidden sm:table-cell">{e.taxNumber ?? '–'}</TableCell>
                <TableCell className="text-right tabular-nums">{e.accountCount}</TableCell>
                <TableCell className="text-right">
                  <Button variant="ghost" size="icon" aria-label={`${e.name} bearbeiten`} onClick={() => setEditing(e)}>
                    <PencilIcon />
                  </Button>
                  {!e.personal && (
                    <Button variant="ghost" size="icon" aria-label={`${e.name} löschen`} onClick={() => remove.mutate(e)}>
                      <Trash2Icon />
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {editing && <LegalEntityDialog key={editing === 'new' ? 'new' : editing.id} entity={editing} onClose={() => setEditing(null)} />}
    </>
  )
}
