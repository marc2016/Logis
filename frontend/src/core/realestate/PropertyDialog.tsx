import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { useLegalEntities } from '@/core/ledger/api'
import { UsageSelect } from './UsageSelect'
import { useInvalidateProperties, type Property, type UnitUsage } from './api'

type Layout = 'SINGLE' | 'MULTI'

/** Create a property (property = null) or edit its name and address (owners only). */
export function PropertyDialog({ property, onClose }: { property: Property | null; onClose: () => void }) {
  const isNew = property === null
  const navigate = useNavigate()
  const { data: entities = [] } = useLegalEntities()
  const [legalEntityId, setLegalEntityId] = useState('')
  const [name, setName] = useState(property?.name ?? '')
  const [street, setStreet] = useState(property?.street ?? '')
  const [postalCode, setPostalCode] = useState(property?.postalCode ?? '')
  const [city, setCity] = useState(property?.city ?? '')
  const [layout, setLayout] = useState<Layout>('SINGLE')
  const [usage, setUsage] = useState<UnitUsage>('VACATION_RENTAL')
  const [firstUnit, setFirstUnit] = useState('')
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateProperties()

  const entityId = legalEntityId || (entities[0] ? String(entities[0].id) : '')

  const save = useMutation({
    mutationFn: () => {
      const address = { name, street, postalCode, city }
      return isNew
        ? api<Property>('/api/properties', {
            json: {
              legalEntityId: Number(entityId),
              ...address,
              multiUnit: layout === 'MULTI',
              firstUnit: { name: layout === 'MULTI' ? firstUnit : null, usage },
            },
          })
        : api<Property>(`/api/properties/${property.id}`, { method: 'PUT', json: address })
    },
    onSuccess: async (saved) => {
      await invalidate()
      toast.success(isNew ? 'Objekt angelegt.' : 'Änderungen gespeichert.')
      onClose()
      if (isNew) navigate(`/immobilien/${saved.id}`)
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
            <DialogTitle>{isNew ? 'Objekt anlegen' : 'Objekt bearbeiten'}</DialogTitle>
          </DialogHeader>
          {isNew && (
            <div className="space-y-2">
              <Label htmlFor="prop-entity">Eigentümer</Label>
              <Select value={entityId} onValueChange={setLegalEntityId}>
                <SelectTrigger id="prop-entity" className="w-full">
                  <SelectValue placeholder="Eigentümer wählen" />
                </SelectTrigger>
                <SelectContent>
                  {entities.map((e) => (
                    <SelectItem key={e.id} value={String(e.id)}>
                      {e.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="prop-name">Name</Label>
            <Input id="prop-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="z. B. Ferienhaus Ostsee" required autoFocus />
          </div>
          <div className="space-y-2">
            <Label htmlFor="prop-street">Straße und Hausnummer</Label>
            <Input id="prop-street" value={street} onChange={(e) => setStreet(e.target.value)} />
          </div>
          <div className="grid gap-4 sm:grid-cols-[8rem_1fr]">
            <div className="space-y-2">
              <Label htmlFor="prop-postal">PLZ</Label>
              <Input id="prop-postal" value={postalCode} onChange={(e) => setPostalCode(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="prop-city">Ort</Label>
              <Input id="prop-city" value={city} onChange={(e) => setCity(e.target.value)} />
            </div>
          </div>
          {isNew && (
            <>
              <ToggleGroup
                type="single"
                variant="outline"
                value={layout}
                onValueChange={(v) => v && setLayout(v as Layout)}
                className="w-full"
                aria-label="Aufteilung"
              >
                <ToggleGroupItem value="SINGLE" className="flex-1">
                  Eine Einheit
                </ToggleGroupItem>
                <ToggleGroupItem value="MULTI" className="flex-1">
                  Mehrere Einheiten
                </ToggleGroupItem>
              </ToggleGroup>
              {layout === 'MULTI' && (
                <div className="space-y-2">
                  <Label htmlFor="prop-first-unit">Erste Einheit</Label>
                  <Input id="prop-first-unit" value={firstUnit} onChange={(e) => setFirstUnit(e.target.value)} placeholder="z. B. Whg. 1" required />
                </div>
              )}
              <div className="space-y-2">
                <Label htmlFor="prop-usage">Nutzung</Label>
                <UsageSelect id="prop-usage" value={usage} onChange={setUsage} />
              </div>
            </>
          )}
          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Abbrechen
            </Button>
            <Button type="submit" disabled={save.isPending || (isNew && !entityId)}>
              Speichern
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
