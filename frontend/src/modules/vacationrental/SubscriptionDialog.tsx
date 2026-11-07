import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { useCalendarLinks, type CalendarLink } from './api'

interface Props {
  unitId: number
  unitName: string
  onClose: () => void
}

const created = new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium' })

/** Subscription links of one unit: create (the address is shown once), list and revoke. */
export function SubscriptionDialog({ unitId, unitName, onClose }: Props) {
  const queryClient = useQueryClient()
  const { data: links = [] } = useCalendarLinks(unitId)
  const [name, setName] = useState('')
  const [withGuests, setWithGuests] = useState(false)
  const [fresh, setFresh] = useState<string | null>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['vacationrental', 'calendar-links'] })

  const create = useMutation({
    mutationFn: () =>
      api<{ link: CalendarLink; path: string }>('/api/vacationrental/calendar-links', { json: { unitId, name, withGuests } }),
    onSuccess: (result) => {
      setFresh(window.location.origin + result.path)
      setName('')
      setWithGuests(false)
      void refresh()
    },
  })
  const revoke = useMutation({
    mutationFn: (id: number) => api<void>(`/api/vacationrental/calendar-links/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      toast.success('Link widerrufen.')
      void refresh()
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate()
  }

  async function copy() {
    if (!fresh) return
    await navigator.clipboard.writeText(fresh)
    toast.success('Link kopiert.')
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Kalender abonnieren</DialogTitle>
          <DialogDescription>
            Belegung von {unitName} in Google, Apple oder Outlook Kalender. Wer den Link kennt, kann die Termine lesen.
          </DialogDescription>
        </DialogHeader>

        {fresh && (
          <div className="space-y-2 rounded-lg border bg-muted/40 p-3" role="status">
            <p className="text-sm font-medium">Neuer Link – er wird nur jetzt angezeigt:</p>
            <p className="font-mono text-xs break-all" aria-label="Neuer Link">
              {fresh}
            </p>
            <div className="flex flex-wrap gap-2">
              <Button size="sm" onClick={copy}>
                Kopieren
              </Button>
              <Button size="sm" variant="outline" asChild>
                <a href={fresh.replace(/^https?:/, 'webcal:')}>In Kalender-App öffnen</a>
              </Button>
            </div>
          </div>
        )}

        <form onSubmit={submit} className="space-y-3">
          <div className="space-y-1">
            <Label htmlFor="link-name">Name</Label>
            <Input id="link-name" value={name} maxLength={80} placeholder="z. B. Reinigung" onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="flex items-center gap-2">
            <Checkbox id="link-guests" checked={withGuests} onCheckedChange={(c) => setWithGuests(c === true)} />
            <Label htmlFor="link-guests">Mit Gastnamen und Buchungsnummer</Label>
          </div>
          <FormError message={create.error ? errorMessage(create.error) : null} />
          <Button type="submit" disabled={create.isPending || name.trim() === ''}>
            Link erstellen
          </Button>
        </form>

        <div className="space-y-2">
          <h3 className="text-sm font-medium">Meine Links</h3>
          {links.length === 0 ? (
            <p className="text-sm text-muted-foreground">Noch keine Links.</p>
          ) : (
            <ul className="divide-y rounded-lg border" aria-label="Meine Links">
              {links.map((link) => (
                <li key={link.id} className="flex items-center justify-between gap-3 p-2 text-sm">
                  <div className="min-w-0">
                    <div className="truncate font-medium">{link.name}</div>
                    <div className="text-xs text-muted-foreground">
                      {link.withGuests ? 'mit Gastdaten' : 'ohne Gastdaten'} · erstellt {created.format(new Date(link.createdAt))}
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={revoke.isPending}
                    onClick={() => revoke.mutate(link.id)}
                    aria-label={`${link.name} widerrufen`}
                  >
                    Widerrufen
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
