import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { ConfirmButton } from '@/components/ConfirmButton'
import { DocumentList, PendingDocuments } from '@/components/DocumentList'
import { uploadDocument } from '@/components/documents'
import { FormError } from '@/components/FormError'
import { ReviewPanel } from './ReviewPanel'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { parseAmount, toInputValue } from '@/lib/money'
import { formatDate, todayIso } from '@/core/ledger/transactions/format'
import {
  channelLabel,
  EMPTY_GUEST,
  statusLabel,
  unitLabel,
  useInvalidateRental,
  useRentalUnits,
  type Booking,
  type Channel,
  type Guest,
} from './api'

export const BOOKING_DOCUMENT_TYPE = 'vacationrental.booking'
const NO_CHANNEL = 'none'

interface Props {
  /** Existing booking, or null for a new one. */
  booking: Booking | null
  /** Preselects the unit (or the first unit of the property) for a new booking. */
  defaultUnitId?: number
  defaultPropertyId?: number
  onClose: () => void
}

function addDays(iso: string, days: number) {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

/** Create, edit or view a booking; the same dialog opens from the grid and the calendar. */
export function BookingDialog({ booking: initial, defaultUnitId, defaultPropertyId, onClose }: Props) {
  const { data: units = [] } = useRentalUnits()
  const [booking, setBooking] = useState<Booking | null>(initial)
  const isNew = booking === null
  const canEdit = isNew || booking.canEdit
  const selectable = units.filter((u) => !u.archived && (u.myRole === 'EDITOR' || u.myRole === 'OWNER'))
  const preset =
    defaultUnitId ?? selectable.find((u) => defaultPropertyId === undefined || u.propertyId === defaultPropertyId)?.id

  const [unitId, setUnitId] = useState<string>(String(initial?.unit.id ?? preset ?? ''))
  const [arrival, setArrival] = useState(initial?.arrival ?? todayIso())
  const [departure, setDeparture] = useState(initial?.departure ?? addDays(todayIso(), 1))
  const [externalRef, setExternalRef] = useState(initial?.externalRef ?? '')
  const [channel, setChannel] = useState<string>(initial?.channel ?? NO_CHANNEL)
  const [adults, setAdults] = useState(String(initial?.adults ?? 2))
  const [children, setChildren] = useState(String(initial?.children ?? 0))
  const [agency, setAgency] = useState(initial?.agency ?? '')
  const [pets, setPets] = useState(String(initial?.pets ?? 0))
  const [rent, setRent] = useState(initial?.expectedRent ? toInputValue(initial.expectedRent) : '')
  const [guest, setGuest] = useState<Guest>(initial?.guest ?? EMPTY_GUEST)
  const [pending, setPending] = useState<File[]>([])
  const [error, setError] = useState<string | null>(null)
  const [uploadErrors, setUploadErrors] = useState<string[]>([])
  const invalidate = useInvalidateRental()

  // A unit chosen before the units loaded, or the booking's own unit, may be missing from the editable list.
  if (!unitId && preset) setUnitId(String(preset))

  const save = useMutation({
    mutationFn: async (expectedRent: string | null) => {
      const body = {
        unitId: Number(unitId),
        arrival,
        departure,
        externalRef,
        channel: channel === NO_CHANNEL ? null : channel,
        adults: Number(adults),
        children: Number(children || 0),
        agency,
        pets: Number(pets || 0),
        expectedRent,
        guest,
        version: booking?.version,
      }
      const saved = isNew
        ? await api<Booking>('/api/vacationrental/bookings', { json: body })
        : await api<Booking>(`/api/vacationrental/bookings/${booking.id}`, { method: 'PUT', json: body })
      const failed: string[] = []
      for (const file of pending) {
        try {
          await uploadDocument(BOOKING_DOCUMENT_TYPE, saved.id, file)
        } catch (err) {
          failed.push(`${file.name}: ${errorMessage(err)}`)
        }
      }
      return { saved, failed }
    },
    onSuccess: async ({ saved, failed }) => {
      await invalidate()
      setPending([])
      if (failed.length) {
        // The booking is saved; keep the dialog open on it so the files can be tried again.
        setBooking(saved)
        setUploadErrors(failed)
        toast.error('Die Buchung wurde gespeichert, aber nicht alle Dokumente.')
        return
      }
      toast.success(isNew ? 'Buchung erfasst.' : 'Buchung gespeichert.')
      onClose()
    },
    onError: (err) => setError(errorMessage(err)),
  })

  const action = useMutation({
    mutationFn: ({ url, method }: { url: string; method: string; done: string; close?: boolean }) =>
      api<Booking | undefined>(url, { method }),
    onSuccess: async (result, { done, close }) => {
      await invalidate()
      toast.success(done)
      if (close) onClose()
      else if (result) {
        setBooking(result)
        setGuest(result.guest ?? EMPTY_GUEST)
      }
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    let expectedRent: string | null = null
    if (rent.trim()) {
      expectedRent = parseAmount(rent)
      if (expectedRent === null || expectedRent.startsWith('-')) {
        setError('Bitte einen gültigen Mietpreis eingeben, z. B. 248,58.')
        return
      }
    }
    save.mutate(expectedRent)
  }

  const g = (field: keyof Guest) => ({
    value: guest[field] ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => setGuest({ ...guest, [field]: e.target.value }),
    disabled: !canEdit,
  })
  const unitOptions = booking && !selectable.some((u) => u.id === booking.unit.id) ? [{ ...booking.unit, archived: false }, ...selectable] : selectable
  const base = booking ? `/api/vacationrental/bookings/${booking.id}` : ''

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{isNew ? 'Buchung erfassen' : canEdit ? 'Buchung bearbeiten' : 'Buchung'}</DialogTitle>
            {booking && (
              <DialogDescription className="flex flex-wrap items-center gap-2">
                <Badge variant={booking.status === 'CANCELLED' ? 'secondary' : 'outline'}>{statusLabel[booking.status]}</Badge>
                {booking.reviewState !== 'NONE' && <Badge>zu prüfen</Badge>}
                {formatDate(booking.arrival)} – {formatDate(booking.departure)} · {booking.nights} {booking.nights === 1 ? 'Nacht' : 'Nächte'}
              </DialogDescription>
            )}
          </DialogHeader>

          {booking && canEdit && booking.reviewState !== 'NONE' && (
            <ReviewPanel
              booking={booking}
              onChanged={(next) => {
                if (!next) onClose()
                else {
                  setBooking(next)
                  setArrival(next.arrival)
                  setDeparture(next.departure)
                  setAdults(String(next.adults))
                  setChildren(String(next.children))
                  setPets(String(next.pets))
                  setRent(next.expectedRent ? toInputValue(next.expectedRent) : '')
                  setGuest(next.guest ?? EMPTY_GUEST)
                }
              }}
            />
          )}

          <fieldset disabled={!canEdit} className="space-y-4">
            <legend className="sr-only">Aufenthalt</legend>
            <div className="space-y-2">
              <Label htmlFor="bk-unit">Einheit</Label>
              <Select value={unitId} onValueChange={setUnitId} disabled={!canEdit || (booking?.status === 'SETTLED')}>
                <SelectTrigger id="bk-unit" className="w-full">
                  <SelectValue placeholder="Einheit wählen" />
                </SelectTrigger>
                <SelectContent>
                  {unitOptions.map((u) => (
                    <SelectItem key={u.id} value={String(u.id)}>
                      {unitLabel(u)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {isNew && selectable.length === 0 && (
                <p className="text-sm text-muted-foreground">Keine Ferienwohnung mit Bearbeitungsrecht vorhanden.</p>
              )}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="bk-arrival">Anreise</Label>
                <Input id="bk-arrival" type="date" value={arrival} onChange={(e) => setArrival(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bk-departure">Abreise</Label>
                <Input id="bk-departure" type="date" value={departure} onChange={(e) => setDeparture(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bk-ref">Buchungsnummer</Label>
                <Input id="bk-ref" value={externalRef} onChange={(e) => setExternalRef(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bk-channel">Kanal</Label>
                <Select value={channel} onValueChange={setChannel} disabled={!canEdit}>
                  <SelectTrigger id="bk-channel" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NO_CHANNEL}>Kein Kanal</SelectItem>
                    {(Object.keys(channelLabel) as Channel[]).map((c) => (
                      <SelectItem key={c} value={c}>
                        {channelLabel[c]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="bk-adults">Erwachsene</Label>
                <Input id="bk-adults" type="number" min={1} max={99} value={adults} onChange={(e) => setAdults(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bk-children">Kinder</Label>
                <Input id="bk-children" type="number" min={0} max={99} value={children} onChange={(e) => setChildren(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bk-pets">Haustiere</Label>
                <Input id="bk-pets" type="number" min={0} max={20} value={pets} onChange={(e) => setPets(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bk-agency">Vermittler</Label>
                <Input id="bk-agency" value={agency} onChange={(e) => setAgency(e.target.value)} placeholder="z. B. Fewo & Meer" />
              </div>
              <div className="space-y-2">
                <Label htmlFor="bk-rent">Mietpreis (€)</Label>
                <Input id="bk-rent" inputMode="decimal" placeholder="0,00" value={rent} onChange={(e) => setRent(e.target.value)} />
              </div>
            </div>
          </fieldset>

          <Separator />
          <section className="space-y-3" aria-label="Gast">
            <h3 className="text-sm font-medium">Gast</h3>
            {booking?.guestDeleted && !guest.name ? (
              <p className="text-sm text-muted-foreground">Gastdaten gelöscht</p>
            ) : null}
            <div className="space-y-2">
              <Label htmlFor="bk-guest-name">Name</Label>
              <Input id="bk-guest-name" {...g('name')} />
            </div>
            {canEdit && (
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="bk-guest-street">Straße</Label>
                  <Input id="bk-guest-street" {...g('street')} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="bk-guest-postal">PLZ</Label>
                  <Input id="bk-guest-postal" {...g('postalCode')} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="bk-guest-city">Ort</Label>
                  <Input id="bk-guest-city" {...g('city')} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="bk-guest-country">Land</Label>
                  <Input id="bk-guest-country" {...g('country')} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="bk-guest-phone">Telefon</Label>
                  <Input id="bk-guest-phone" type="tel" {...g('phone')} />
                </div>
                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="bk-guest-email">E-Mail</Label>
                  <Input id="bk-guest-email" type="email" {...g('email')} />
                </div>
              </div>
            )}
          </section>

          {canEdit && (
            <>
              <Separator />
              <section className="space-y-2" aria-label="Dokumente">
                <h3 className="text-sm font-medium">Dokumente</h3>
                {booking ? (
                  <DocumentList ownerType={BOOKING_DOCUMENT_TYPE} ownerId={booking.id} canEdit />
                ) : (
                  <PendingDocuments files={pending} onChange={setPending} />
                )}
                {uploadErrors.length > 0 && (
                  <ul className="text-sm text-destructive" role="alert">
                    {uploadErrors.map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                )}
              </section>
            </>
          )}

          {booking && canEdit && (
            <>
              <Separator />
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={action.isPending}
                  onClick={() =>
                    action.mutate(
                      booking.status === 'CANCELLED'
                        ? { url: `${base}/reinstate`, method: 'POST', done: 'Stornierung aufgehoben.' }
                        : { url: `${base}/cancel`, method: 'POST', done: 'Buchung storniert.' },
                    )
                  }
                >
                  {booking.status === 'CANCELLED' ? 'Stornierung aufheben' : 'Stornieren'}
                </Button>
                {!booking.guestDeleted && (
                  <ConfirmButton
                    question="Name und Kontaktdaten endgültig löschen? Dokumente bleiben erhalten."
                    disabled={action.isPending}
                    onConfirm={() => action.mutate({ url: `${base}/guest`, method: 'DELETE', done: 'Gastdaten gelöscht. Dokumente bitte bei Bedarf einzeln löschen.' })}
                  >
                    Gastdaten löschen
                  </ConfirmButton>
                )}
                {booking.status !== 'SETTLED' && (
                  <ConfirmButton
                    question="Buchung endgültig löschen?"
                    disabled={action.isPending}
                    onConfirm={() => action.mutate({ url: base, method: 'DELETE', done: 'Buchung gelöscht.', close: true })}
                  >
                    Löschen
                  </ConfirmButton>
                )}
              </div>
            </>
          )}

          <FormError message={error} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {canEdit ? 'Abbrechen' : 'Schließen'}
            </Button>
            {canEdit && (
              <Button type="submit" disabled={save.isPending || !unitId}>
                Speichern
              </Button>
            )}
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
