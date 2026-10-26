import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArchiveIcon, ArchiveRestoreIcon, PencilIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { ledgerKeys, useAdminCategories, type Category, type CategoryKind } from '@/core/ledger/api'

const ROOT = 'root'
const kindLabel: Record<CategoryKind, string> = { INCOME: 'Einnahmen', EXPENSE: 'Ausgaben' }

type Editing = { mode: 'new'; kind: CategoryKind; parentId: number | null } | { mode: 'edit'; category: Category }

function CategoryDialog({ editing, roots, onClose }: { editing: Editing; roots: Category[]; onClose: () => void }) {
  const queryClient = useQueryClient()
  const isNew = editing.mode === 'new'
  const kind = isNew ? editing.kind : editing.category.kind
  const [name, setName] = useState(isNew ? '' : editing.category.name)
  const [parent, setParent] = useState<string>(String((isNew ? editing.parentId : editing.category.parentId) ?? ROOT))
  const [error, setError] = useState<string | null>(null)
  const hasChildren = !isNew && editing.category.children.length > 0
  const parents = roots.filter((r) => r.kind === kind && (isNew || r.id !== editing.category.id))

  const save = useMutation({
    mutationFn: () => {
      const parentId = parent === ROOT ? null : Number(parent)
      return isNew
        ? api('/api/admin/categories', { json: { name, kind, parentId } })
        : api(`/api/admin/categories/${editing.category.id}`, { method: 'PUT', json: { name, parentId } })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ledgerKeys.adminCategories }),
        queryClient.invalidateQueries({ queryKey: ledgerKeys.categories }),
      ])
      toast.success(isNew ? 'Kategorie angelegt.' : 'Kategorie gespeichert.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    save.mutate()
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? `Kategorie anlegen (${kindLabel[kind]})` : 'Kategorie bearbeiten'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="cat-name">Name</Label>
            <Input id="cat-name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          </div>
          <div className="space-y-2">
            <Label htmlFor="cat-parent">Hauptkategorie</Label>
            <Select value={parent} onValueChange={setParent} disabled={hasChildren}>
              <SelectTrigger id="cat-parent" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ROOT}>Keine (selbst Hauptkategorie)</SelectItem>
                {parents.map((r) => (
                  <SelectItem key={r.id} value={String(r.id)}>
                    {r.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
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

export function CategoriesPage() {
  const { data: tree = [], isLoading } = useAdminCategories()
  const [editing, setEditing] = useState<Editing | null>(null)
  const queryClient = useQueryClient()

  const action = useMutation({
    mutationFn: ({ category, op }: { category: Category; op: 'archive' | 'restore' | 'delete' }) =>
      op === 'delete'
        ? api(`/api/admin/categories/${category.id}`, { method: 'DELETE' })
        : api(`/api/admin/categories/${category.id}/${op}`, { method: 'POST' }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ledgerKeys.adminCategories }),
        queryClient.invalidateQueries({ queryKey: ledgerKeys.categories }),
      ])
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  function Row({ category, child }: { category: Category; child?: boolean }) {
    const managed = category.moduleId !== null
    return (
      <li className={`flex items-center gap-2 py-2 ${child ? 'pl-6' : ''}`}>
        <span className={`flex-1 ${category.archived ? 'text-muted-foreground line-through' : child ? '' : 'font-medium'}`}>{category.name}</span>
        {managed && <Badge variant="secondary">Modul</Badge>}
        {category.archived && <Badge variant="outline">archiviert</Badge>}
        {!child && !category.archived && (
          <Button variant="ghost" size="icon" aria-label={`Unterkategorie zu ${category.name}`} onClick={() => setEditing({ mode: 'new', kind: category.kind, parentId: category.id })}>
            <PlusIcon />
          </Button>
        )}
        {!managed && (
          <Button variant="ghost" size="icon" aria-label={`${category.name} bearbeiten`} onClick={() => setEditing({ mode: 'edit', category })}>
            <PencilIcon />
          </Button>
        )}
        <Button
          variant="ghost"
          size="icon"
          aria-label={`${category.name} ${category.archived ? 'wiederherstellen' : 'archivieren'}`}
          onClick={() => action.mutate({ category, op: category.archived ? 'restore' : 'archive' })}
        >
          {category.archived ? <ArchiveRestoreIcon /> : <ArchiveIcon />}
        </Button>
        {!managed && (
          <Button variant="ghost" size="icon" aria-label={`${category.name} löschen`} onClick={() => action.mutate({ category, op: 'delete' })}>
            <Trash2Icon />
          </Button>
        )}
      </li>
    )
  }

  return (
    <>
      <PageHeader title="Kategorien" description="Gemeinsamer Kategorienbaum für alle Benutzer. Verwendete Kategorien lassen sich nur archivieren." />
      {isLoading && <p className="text-muted-foreground">Wird geladen …</p>}
      <div className="grid gap-6 lg:grid-cols-2">
        {(['EXPENSE', 'INCOME'] as const).map((kind) => (
          <Card key={kind} role="region" aria-label={kindLabel[kind]}>
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle>{kindLabel[kind]}</CardTitle>
              <Button size="sm" variant="outline" onClick={() => setEditing({ mode: 'new', kind, parentId: null })}>
                <PlusIcon />
                Hauptkategorie
              </Button>
            </CardHeader>
            <CardContent>
              <ul className="divide-y">
                {tree
                  .filter((c) => c.kind === kind)
                  .map((c) => (
                    <li key={c.id}>
                      <ul>
                        <Row category={c} />
                        {c.children.map((child) => (
                          <Row key={child.id} category={child} child />
                        ))}
                      </ul>
                    </li>
                  ))}
              </ul>
            </CardContent>
          </Card>
        ))}
      </div>
      {editing && <CategoryDialog editing={editing} roots={tree} onClose={() => setEditing(null)} />}
    </>
  )
}
