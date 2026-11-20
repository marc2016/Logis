import { useState } from 'react'
import { ArrowDownIcon, ArrowUpIcon, PlusIcon, Settings2Icon, Trash2Icon } from 'lucide-react'
import type { DashboardWidget } from '@/app/types'
import { useActiveModules } from '@/app/useActiveModules'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useCurrentUser } from '@/core/auth/session'
import { cn } from '@/lib/utils'
import { useDashboard, useSaveDashboard, type Tile } from './api'
import { catalogOf, move, newTile, tilesOf } from './catalog'
import { coreWidgets } from './coreWidgets'

/**
 * „Übersicht“: the user's own tiles (spec dashboard). „Anpassen“ adds, moves, configures and removes tiles; every
 * change is saved at once (design D4).
 */
export function DashboardPage() {
  const user = useCurrentUser()
  const { modules, isLoading: modulesLoading } = useActiveModules()
  const { data, isLoading, isError } = useDashboard()
  const save = useSaveDashboard()
  const [editing, setEditing] = useState(false)
  const [adding, setAdding] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [configuring, setConfiguring] = useState<Tile | null>(null)

  const catalog = catalogOf(coreWidgets, modules)
  // Without an answer (e.g. the API failed) the default is shown, so the start page always works.
  const { all, shown } = tilesOf(isError ? null : data?.tiles, catalog)
  const saveTiles = (tiles: Tile[] | null) => save.mutate(tiles)

  return (
    <>
      <PageHeader
        title="Übersicht"
        description={`Willkommen, ${user.displayName}.`}
        actions={
          editing ? (
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setAdding(true)}>
                <PlusIcon />
                Kachel hinzufügen
              </Button>
              <Button variant="outline" onClick={() => setResetting(true)}>
                Standard wiederherstellen
              </Button>
              <Button onClick={() => setEditing(false)}>Fertig</Button>
            </div>
          ) : (
            <Button variant="outline" onClick={() => setEditing(true)} disabled={isLoading || modulesLoading}>
              <Settings2Icon />
              Anpassen
            </Button>
          )
        }
      />

      {!isLoading && !modulesLoading && shown.length === 0 && (
        <p className="text-sm text-muted-foreground">Keine Kacheln. Mit „Anpassen“ lassen sich Kacheln hinzufügen.</p>
      )}

      <div className="grid gap-4 @2xl:grid-cols-2 @5xl:grid-cols-3">
        {!isLoading &&
          !modulesLoading &&
          shown.map((tile, i) => {
            const widget = catalog.get(tile.type)!
            return (
              <TileCard
                key={tile.id}
                widget={widget}
                tile={tile}
                editing={editing}
                first={i === 0}
                last={i === shown.length - 1}
                onUp={() => saveTiles(move(all, shown, tile.id, -1))}
                onDown={() => saveTiles(move(all, shown, tile.id, 1))}
                onSettings={() => setConfiguring(tile)}
                onRemove={() => saveTiles(all.filter((t) => t.id !== tile.id))}
              />
            )
          })}
      </div>

      <AddTileDialog
        open={adding}
        widgets={[...catalog.values()]}
        onClose={() => setAdding(false)}
        onAdd={(widget) => {
          saveTiles([...all, newTile(widget)])
          setAdding(false)
        }}
      />

      {configuring && (
        <SettingsDialog
          tile={configuring}
          widget={catalog.get(configuring.type)!}
          onClose={() => setConfiguring(null)}
          onSave={(settings) => {
            saveTiles(all.map((t) => (t.id === configuring.id ? { ...t, settings } : t)))
            setConfiguring(null)
          }}
        />
      )}

      <Dialog open={resetting} onOpenChange={setResetting}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Standard wiederherstellen?</DialogTitle>
            <DialogDescription>Deine Kacheln und ihre Einstellungen werden durch die Standard-Übersicht ersetzt.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setResetting(false)}>
              Abbrechen
            </Button>
            <Button
              onClick={() => {
                saveTiles(null)
                setResetting(false)
              }}
            >
              Wiederherstellen
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

interface TileCardProps {
  widget: DashboardWidget
  tile: Tile
  editing: boolean
  first: boolean
  last: boolean
  onUp: () => void
  onDown: () => void
  onSettings: () => void
  onRemove: () => void
}

function TileCard({ widget, tile, editing, first, last, onUp, onDown, onSettings, onRemove }: TileCardProps) {
  const { Tile: Content } = widget
  const headingId = `tile-${tile.id}`
  return (
    <section aria-labelledby={headingId} className={cn('min-w-0', widget.wide && '@2xl:col-span-2')}>
      <Card className="h-full">
        <CardHeader>
          <CardTitle>
            <h2 id={headingId} className="text-base font-semibold">
              {widget.title}
            </h2>
          </CardTitle>
          {editing && (
            <CardAction className="flex gap-1">
              <Button variant="ghost" size="icon" aria-label={`${widget.title} nach oben`} disabled={first} onClick={onUp}>
                <ArrowUpIcon />
              </Button>
              <Button variant="ghost" size="icon" aria-label={`${widget.title} nach unten`} disabled={last} onClick={onDown}>
                <ArrowDownIcon />
              </Button>
              {widget.Settings && (
                <Button variant="ghost" size="icon" aria-label={`${widget.title} einstellen`} onClick={onSettings}>
                  <Settings2Icon />
                </Button>
              )}
              <Button variant="ghost" size="icon" aria-label={`${widget.title} entfernen`} onClick={onRemove}>
                <Trash2Icon />
              </Button>
            </CardAction>
          )}
        </CardHeader>
        <CardContent>
          <Content settings={tile.settings} />
        </CardContent>
      </Card>
    </section>
  )
}

function AddTileDialog({ open, widgets, onClose, onAdd }: {
  open: boolean
  widgets: DashboardWidget[]
  onClose: () => void
  onAdd: (widget: DashboardWidget) => void
}) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Kachel hinzufügen</DialogTitle>
          <DialogDescription>Eine Kachel kann auch mehrfach mit unterschiedlichen Einstellungen vorkommen.</DialogDescription>
        </DialogHeader>
        <ul className="divide-y">
          {widgets.map((w) => (
            <li key={w.type} className="flex items-center justify-between gap-3 py-2">
              <div className="min-w-0">
                <p className="font-medium">{w.title}</p>
                <p className="text-sm text-muted-foreground">{w.description}</p>
              </div>
              <Button variant="outline" size="sm" aria-label={`${w.title} hinzufügen`} onClick={() => onAdd(w)}>
                Hinzufügen
              </Button>
            </li>
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  )
}

function SettingsDialog({ tile, widget, onClose, onSave }: {
  tile: Tile
  widget: DashboardWidget
  onClose: () => void
  onSave: (settings: Record<string, unknown>) => void
}) {
  // A copy: nothing is saved until „Übernehmen“.
  const [value, setValue] = useState(tile.settings)
  const Form = widget.Settings!
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{widget.title} einstellen</DialogTitle>
          <DialogDescription>{widget.description}</DialogDescription>
        </DialogHeader>
        <Form value={value} onChange={setValue} />
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Abbrechen
          </Button>
          <Button onClick={() => onSave(value)}>Übernehmen</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
