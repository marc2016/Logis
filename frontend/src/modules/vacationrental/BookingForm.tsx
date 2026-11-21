import { useEffect, useState, type FocusEvent, type FormEvent, type ReactNode } from 'react'
import { DateField } from '@/components/DateField'
import { useMutation } from '@tanstack/react-query'
import { FileSearchIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { DocumentList, PendingDocuments } from '@/components/DocumentList'
import { uploadDocument } from '@/components/documents'
import { FormError } from '@/components/FormError'
import { AgencyPicker } from './AgencyPicker'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { parseAmount, toInputValue } from '@/lib/money'
import { cn } from '@/lib/utils'
import { formatDate, todayIso } from '@/core/ledger/transactions/format'
import {
  EMPTY_GUEST,
  kindLabel,
  unitLabel,
  useInvalidateRental,
  useRentalUnits,
  type AgencyRef,
  type Booking,
  type BookingKind,
  type FieldLocation,
  type Guest,
} from './api'

export const BOOKING_DOCUMENT_TYPE = 'vacationrental.booking'

export interface SaveResult {
  saved: Booking
  /** Documents chosen for a new booking that could not be uploaded. */
  failed: string[]
}

interface Props {
  /** Existing booking, or null for a new one. */
  booking: Booking | null
  /** Preselects the unit (or the first unit of the property) for a new booking. */
  defaultUnitId?: number
  defaultPropertyId?: number
  onSaved: (result: SaveResult) => void
  /** Shows a cancel button next to „Speichern“. */
  onCancel?: () => void
  /** Where each field's value was found; fields with a location can be shown in their source. */
  locations?: Record<string, FieldLocation>
  /** The field whose location is shown, marked in the form. */
  activeField?: string | null
  /** A field with a location was focused or clicked. */
  onFieldFocus?: (field: string) => void
  /** Whether the form holds edits that are not saved yet (design D4 of booking-review-queue). */
  onDirtyChange?: (dirty: boolean) => void
  /** Only dates, persons and rent, plus every field that differs from its document (spec bookings, „Compact view“). */
  compact?: boolean
}

/** The fields of the compact view (design D4 of booking-compact-view). */
const COMPACT_FIELDS = new Set(['arrival', 'departure', 'adults', 'children', 'expectedRent'])

function addDays(iso: string, days: number) {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

/** The source's value in the notation of the form. */
function sourceText(field: string, value: string | null) {
  if (value === null) return '–'
  if (field === 'arrival' || field === 'departure') return formatDate(value)
  if (field === 'expectedRent') return `${toInputValue(value)} €`
  return value
}

interface FieldProps {
  field: string
  label: string
  htmlFor: string
  className?: string
  children: ReactNode
  location?: FieldLocation
  active: boolean
  onShow?: (field: string) => void
}

/** A labelled field; marked when it has a location, with a hint when the value no longer matches the source. */
function Field({ field, label, htmlFor, className, children, location, active, onShow }: FieldProps) {
  return (
    <div
      data-field={field}
      data-active={active ? 'true' : undefined}
      className={cn(
        'space-y-2 rounded-md',
        location && 'data-[active=true]:ring-2 data-[active=true]:ring-primary/60 data-[active=true]:ring-offset-2 data-[active=true]:ring-offset-background',
        className,
      )}
    >
      <div className="flex items-center gap-1.5">
        <Label htmlFor={htmlFor}>{label}</Label>
        {location && onShow && (
          <button
            type="button"
            className="text-muted-foreground hover:text-foreground"
            aria-label={`${label} in der Quelle zeigen`}
            title="In der Quelle zeigen"
            onClick={() => onShow(field)}
          >
            <FileSearchIcon className="size-3.5" />
          </button>
        )}
      </div>
      {children}
      {location?.differs && (
        <p className="text-xs text-amber-700 dark:text-amber-400">weicht vom Dokument ab · laut Dokument: {sourceText(field, location.sourceValue)}</p>
      )}
    </div>
  )
}

/** The booking's fields; used by the booking page and the dialog for a new booking. */
export function BookingForm({ booking, defaultUnitId, defaultPropertyId, onSaved, onCancel, locations = {}, activeField, onFieldFocus, onDirtyChange, compact = false }: Props) {
  const { data: units = [] } = useRentalUnits()
  const isNew = booking === null
  const canEdit = isNew || booking.canEdit
  const selectable = units.filter((u) => !u.archived && (u.myRole === 'EDITOR' || u.myRole === 'OWNER'))
  const preset = defaultUnitId ?? selectable.find((u) => defaultPropertyId === undefined || u.propertyId === defaultPropertyId)?.id

  const [kind, setKind] = useState<BookingKind>(booking?.kind ?? 'GUEST')
  const owner = kind === 'OWNER'
  const [unitId, setUnitId] = useState<string>(String(booking?.unit.id ?? preset ?? ''))
  const [arrival, setArrival] = useState(booking?.arrival ?? todayIso())
  const [departure, setDeparture] = useState(booking?.departure ?? addDays(todayIso(), 1))
  const [externalRef, setExternalRef] = useState(booking?.externalRef ?? '')
  const [adults, setAdults] = useState(String(booking?.adults ?? 2))
  const [children, setChildren] = useState(String(booking?.children ?? 0))
  const [agency, setAgency] = useState<AgencyRef | null>(booking?.agency ?? null)
  const [pets, setPets] = useState(String(booking?.pets ?? 0))
  const [rent, setRent] = useState(booking?.expectedRent ? toInputValue(booking.expectedRent) : '')
  const [guest, setGuest] = useState<Guest>(booking?.guest ?? EMPTY_GUEST)
  const [pending, setPending] = useState<File[]>([])
  const [error, setError] = useState<string | null>(null)
  const invalidate = useInvalidateRental()

  // Edits not saved yet: the values differ from the stored booking, or documents wait for upload.
  const values = JSON.stringify([kind, unitId, arrival, departure, externalRef, adults, children, agency?.id ?? null, pets, rent, guest])
  const [initial] = useState(values)
  const dirty = values !== initial || pending.length > 0
  useEffect(() => {
    onDirtyChange?.(dirty)
  }, [dirty, onDirtyChange])

  // A unit chosen before the units loaded may be missing from the editable list.
  if (!unitId && preset) setUnitId(String(preset))

  const save = useMutation({
    mutationFn: async (expectedRent: string | null): Promise<SaveResult> => {
      const body = {
        unitId: Number(unitId),
        arrival,
        departure,
        externalRef,
        adults: Number(adults),
        children: Number(children || 0),
        agencyId: agency?.id ?? null,
        pets: Number(pets || 0),
        expectedRent,
        guest,
        version: booking?.version,
        kind,
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
    onSuccess: async (result) => {
      await invalidate()
      setPending([])
      onSaved(result)
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    let expectedRent: string | null = null
    if (!owner && rent.trim()) {
      expectedRent = parseAmount(rent)
      if (expectedRent === null || expectedRent.startsWith('-')) {
        setError('Bitte einen gültigen Mietpreis eingeben, z. B. 248,58.')
        return
      }
    }
    save.mutate(expectedRent)
  }

  /** Focus anywhere inside a field reports that field, if it has a location. */
  function focused(e: FocusEvent<HTMLFormElement>) {
    const field = (e.target as HTMLElement).closest<HTMLElement>('[data-field]')?.dataset.field
    if (field && locations[field] && onFieldFocus) onFieldFocus(field)
  }

  const at = (field: string) => ({ location: locations[field], active: activeField === field, onShow: onFieldFocus })
  // In the compact view a field is shown if it belongs to it or differs from its document; its value is kept either way.
  const shown = (field: string) => !compact || COMPACT_FIELDS.has(field) || locations[field]?.differs === true
  const guestFieldsShown = ['guestName', 'guestStreet', 'guestPostalCode', 'guestCity', 'guestCountry', 'guestPhone', 'guestEmail'].some(shown)

  const g = (field: keyof Guest) => ({
    value: guest[field] ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => setGuest({ ...guest, [field]: e.target.value }),
    disabled: !canEdit,
  })
  const unitOptions = booking && !selectable.some((u) => u.id === booking.unit.id) ? [{ ...booking.unit, archived: false }, ...selectable] : selectable

  return (
    <form onSubmit={submit} onFocus={focused} className="space-y-4" aria-label={isNew ? 'Buchung erfassen' : 'Buchung'}>
      <fieldset disabled={!canEdit} className="space-y-4">
        <legend className="sr-only">Aufenthalt</legend>
        {isNew && !compact && (
          <ToggleGroup type="single" variant="outline" value={kind} onValueChange={(v) => v && setKind(v as BookingKind)} aria-label="Art der Buchung">
            {(['GUEST', 'OWNER'] as const).map((k) => (
              <ToggleGroupItem key={k} value={k}>
                {kindLabel[k]}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
        )}
        {shown('propertyName') && (
        <Field {...at('propertyName')} field="propertyName" label="Einheit" htmlFor="bk-unit">
          <Select value={unitId} onValueChange={setUnitId} disabled={!canEdit || booking?.status === 'SETTLED'}>
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
          {isNew && selectable.length === 0 && <p className="text-sm text-muted-foreground">Keine Ferienwohnung mit Bearbeitungsrecht vorhanden.</p>}
        </Field>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field {...at('arrival')} field="arrival" label="Anreise" htmlFor="bk-arrival">
            <DateField id="bk-arrival" label="Anreise" value={arrival} onChange={setArrival} required />
          </Field>
          <Field {...at('departure')} field="departure" label="Abreise" htmlFor="bk-departure">
            <DateField id="bk-departure" label="Abreise" value={departure} onChange={setDeparture} required />
          </Field>
          {shown('externalRef') && (
            <Field {...at('externalRef')} field="externalRef" label="Buchungsnummer" htmlFor="bk-ref">
              <Input id="bk-ref" value={externalRef} onChange={(e) => setExternalRef(e.target.value)} />
            </Field>
          )}
          {shown('agency') && (
            <Field {...at('agency')} field="agency" label="Vermittler" htmlFor="bk-agency">
              <AgencyPicker id="bk-agency" value={agency} onChange={setAgency} disabled={!canEdit} onError={setError} />
            </Field>
          )}
          {!owner && (
            <>
              <Field {...at('adults')} field="adults" label="Erwachsene" htmlFor="bk-adults">
                <Input id="bk-adults" type="number" min={1} max={99} value={adults} onChange={(e) => setAdults(e.target.value)} required />
              </Field>
              <Field {...at('children')} field="children" label="Kinder" htmlFor="bk-children">
                <Input id="bk-children" type="number" min={0} max={99} value={children} onChange={(e) => setChildren(e.target.value)} />
              </Field>
              {shown('pets') && (
                <Field {...at('pets')} field="pets" label="Haustiere" htmlFor="bk-pets">
                  <Input id="bk-pets" type="number" min={0} max={20} value={pets} onChange={(e) => setPets(e.target.value)} />
                </Field>
              )}
              <Field {...at('expectedRent')} field="expectedRent" label="Mietpreis (€)" htmlFor="bk-rent">
                <Input id="bk-rent" inputMode="decimal" placeholder="0,00" value={rent} onChange={(e) => setRent(e.target.value)} />
              </Field>
            </>
          )}
        </div>
      </fieldset>

      {!owner && guestFieldsShown && (
        <>
          <Separator />
          <section className="space-y-3" aria-label="Gast">
            <h3 className="text-sm font-medium">Gast</h3>
            {booking?.guestDeleted && !guest.name ? <p className="text-sm text-muted-foreground">Gastdaten gelöscht</p> : null}
            {shown('guestName') && (
              <Field {...at('guestName')} field="guestName" label="Name" htmlFor="bk-guest-name">
                <Input id="bk-guest-name" {...g('name')} />
              </Field>
            )}
            {canEdit && (
              <div className="grid gap-4 sm:grid-cols-2">
                {shown('guestStreet') && (
                <Field {...at('guestStreet')} field="guestStreet" label="Straße" htmlFor="bk-guest-street" className="sm:col-span-2">
                  <Input id="bk-guest-street" {...g('street')} />
                </Field>
                )}
                {shown('guestPostalCode') && (
                <Field {...at('guestPostalCode')} field="guestPostalCode" label="PLZ" htmlFor="bk-guest-postal">
                  <Input id="bk-guest-postal" {...g('postalCode')} />
                </Field>
                )}
                {shown('guestCity') && (
                <Field {...at('guestCity')} field="guestCity" label="Ort" htmlFor="bk-guest-city">
                  <Input id="bk-guest-city" {...g('city')} />
                </Field>
                )}
                {shown('guestCountry') && (
                <Field {...at('guestCountry')} field="guestCountry" label="Land" htmlFor="bk-guest-country">
                  <Input id="bk-guest-country" {...g('country')} />
                </Field>
                )}
                {shown('guestPhone') && (
                <Field {...at('guestPhone')} field="guestPhone" label="Telefon" htmlFor="bk-guest-phone">
                  <Input id="bk-guest-phone" type="tel" {...g('phone')} />
                </Field>
                )}
                {shown('guestEmail') && (
                <Field {...at('guestEmail')} field="guestEmail" label="E-Mail" htmlFor="bk-guest-email" className="sm:col-span-2">
                  <Input id="bk-guest-email" type="email" {...g('email')} />
                </Field>
                )}
              </div>
            )}
          </section>
        </>
      )}

      {canEdit && !compact && (
        <>
          <Separator />
          <section className="space-y-2" aria-label="Dokumente">
            <h3 className="text-sm font-medium">Dokumente</h3>
            {booking ? (
              <DocumentList ownerType={BOOKING_DOCUMENT_TYPE} ownerId={booking.id} canEdit />
            ) : (
              <PendingDocuments files={pending} onChange={setPending} />
            )}
          </section>
        </>
      )}

      <FormError message={error} />
      {(canEdit || onCancel) && (
        <div className="flex justify-end gap-2">
          {onCancel && (
            <Button type="button" variant="outline" onClick={onCancel}>
              {canEdit ? 'Abbrechen' : 'Schließen'}
            </Button>
          )}
          {canEdit && (
            <Button type="submit" disabled={save.isPending || !unitId}>
              Speichern
            </Button>
          )}
        </div>
      )}
    </form>
  )
}
