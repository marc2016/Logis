import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ArrowLeftIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ConfirmButton } from '@/components/ConfirmButton'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Money } from '@/components/Money'
import { ApiError, api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/core/ledger/transactions/format'
import { BookingForm } from '../BookingForm'
import { ReviewPanel } from '../ReviewPanel'
import { ReviewBar } from '../ReviewBar'
import { REVIEW_PARAM, listPath, nextAfter, previousBefore, queueParams, reviewPath, reviewStartPath, useReviewQueue } from '../reviewQueue'
import { SourcePanel } from '../SourcePanel'
import type { ActiveField } from '../sources'
import { backLabel, type BackState } from '../BookingPage.paths'
import { statusLabel, unitLabel, useBooking, useInvalidateRental, type Booking } from '../api'

const LIST = '/ferienvermietung/buchungen'

/** Puts the cursor into a field of the form, e.g. after clicking its passage in the source. */
function focusField(field: string) {
  const target = document.querySelector<HTMLElement>(`[data-field="${field}"] input, [data-field="${field}"] button[role="combobox"]`)
  target?.focus()
}

function Header({ booking }: { booking: Booking }) {
  const guest =
    booking.kind === 'OWNER' ? 'Eigenbelegung' : booking.guest?.name || (booking.guestDeleted ? 'Gastdaten gelöscht' : 'Ohne Gastname')
  return (
    <header className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">{guest}</h1>
        <Badge variant={booking.status === 'CANCELLED' ? 'secondary' : 'outline'}>{statusLabel[booking.status]}</Badge>
        {booking.reviewState !== 'NONE' && <Badge>zu prüfen</Badge>}
      </div>
      <dl className="flex flex-wrap gap-x-6 gap-y-1 text-sm" aria-label="Wichtige Angaben">
        <div>
          <dt className="sr-only">Zeitraum</dt>
          <dd className="font-medium">
            {formatDate(booking.arrival)} – {formatDate(booking.departure)} · {booking.nights} {booking.nights === 1 ? 'Nacht' : 'Nächte'}
          </dd>
        </div>
        <div>
          <dt className="sr-only">Einheit</dt>
          <dd>{unitLabel(booking.unit)}</dd>
        </div>
        {booking.agency && (
          <div>
            <dt className="sr-only">Vermittler</dt>
            <dd>{booking.agency.name}</dd>
          </div>
        )}
        {booking.externalRef && (
          <div>
            <dt className="sr-only">Buchungsnummer</dt>
            <dd className="text-muted-foreground">Nr. {booking.externalRef}</dd>
          </div>
        )}
        {booking.expectedRent && (
          <div>
            <dt className="sr-only">Mietpreis</dt>
            <dd className="font-medium">
              <Money amount={booking.expectedRent} colored={false} />
            </dd>
          </div>
        )}
      </dl>
    </header>
  )
}

/** Arrow keys only move while nothing else wants them: no field, dialog or popover has the focus (design D4). */
function arrowFree(e: KeyboardEvent) {
  if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return false
  const target = e.target instanceof Element ? e.target : null
  if (target?.closest('input, textarea, select, [contenteditable="true"], [role="combobox"], [role="listbox"], [role="slider"]')) return false
  return !document.querySelector('[role="dialog"], [data-radix-popper-content-wrapper]')
}

/** A booking as its own page: header, data and, for editors, its sources with the location of every value. */
export function BookingPage() {
  const { bookingId } = useParams()
  const id = Number(bookingId)
  const location = useLocation()
  const navigate = useNavigate()
  const back = (location.state as BackState | null)?.back ?? LIST
  const { data: booking, error, isLoading } = useBooking(Number.isFinite(id) ? id : null)
  const invalidate = useInvalidateRental()
  const [active, setActive] = useState<ActiveField | null>(null)
  const [sourceId, setSourceId] = useState<number | null>(null)
  const [tab, setTab] = useState('data')

  // Review mode (spec bookings, „Review mode“): the queue of bookings to review lives in the address.
  const [params] = useSearchParams()
  const reviewing = params.get(REVIEW_PARAM) === '1'
  const filters = queueParams(params)
  const queue = useReviewQueue(filters, reviewing)
  const [dirty, setDirty] = useState(false)
  const [leaveTo, setLeaveTo] = useState<string | null>(null)
  const [shown, setShown] = useState(id)
  if (id !== shown) {
    // Another booking of the queue: nothing of the previous one stays selected.
    setShown(id)
    setActive(null)
    setSourceId(null)
    setTab('data')
    setDirty(false)
  }
  const items = queue.data?.items ?? []
  const previous = booking && reviewing ? previousBefore(items, booking) : undefined
  const next = booking && reviewing ? nextAfter(items, booking) : undefined
  const index = booking ? items.findIndex((b) => b.id === booking.id) : -1

  /** Leaves the booking, asking first if it has unsaved edits. */
  function go(path: string) {
    if (dirty) setLeaveTo(path)
    else navigate(path)
  }

  /** After a decision: the booking that followed it in the queue, or „Alles geprüft.“ (design D2). */
  async function moveOn(current: Booking) {
    const fresh = (await queue.refetch()).data?.items ?? []
    const following = nextAfter(fresh, current) ?? fresh[0]
    navigate(following ? reviewPath(following.id, filters) : reviewStartPath(filters))
  }

  useEffect(() => {
    if (!reviewing) return
    function onKey(e: KeyboardEvent) {
      if ((e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') || !arrowFree(e)) return
      const target = e.key === 'ArrowLeft' ? previous : next
      if (!target) return
      e.preventDefault()
      if (dirty) setLeaveTo(reviewPath(target.id, filters))
      else navigate(reviewPath(target.id, filters))
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  const action = useMutation({
    mutationFn: ({ url, method }: { url: string; method: string; done: string; leave?: boolean }) => api<Booking | undefined>(url, { method }),
    onSuccess: async (_, { done, leave }) => {
      await invalidate()
      toast.success(done)
      if (leave && reviewing && booking) await moveOn(booking)
      else if (leave) navigate(back)
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-96" />
      </div>
    )
  }
  if (!booking) {
    const notFound = !Number.isFinite(id) || (error instanceof ApiError && error.status === 404)
    return (
      <div className="space-y-4">
        <Link to={back} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeftIcon className="size-4" /> {backLabel(back)}
        </Link>
        <p role="alert">{notFound ? 'Buchung nicht gefunden' : errorMessage(error)}</p>
      </div>
    )
  }

  const locations = booking.locations ?? {}
  const proposalLocations = booking.proposalLocations ?? {}
  const sources = booking.sources ?? []
  const withSources = booking.canEdit
  const base = `/api/vacationrental/bookings/${booking.id}`

  /** Shows a location in its source; on narrow screens the source tab stays where the user is. */
  function show(next: ActiveField) {
    const at = next.scope === 'proposal' ? proposalLocations[next.field] : locations[next.field]
    if (!at) return
    setActive(next)
    setSourceId(at.sourceId)
  }

  const form = (
    <div className="space-y-6">
      <BookingForm
        // A new version (saved, confirmed, change applied) resets the form to the stored values.
        key={`${booking.id}:${booking.version}:${booking.reviewState}`}
        booking={booking}
        locations={withSources ? locations : {}}
        activeField={active?.scope === 'current' ? active.field : null}
        onFieldFocus={(field) => show({ field, scope: 'current' })}
        onDirtyChange={setDirty}
        onSaved={({ failed }) => {
          if (failed.length) toast.error('Die Buchung wurde gespeichert, aber nicht alle Dokumente.')
          else toast.success('Buchung gespeichert.')
        }}
      />
      {booking.canEdit && (
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
            {!booking.guestDeleted && booking.kind !== 'OWNER' && (
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
                onConfirm={() => action.mutate({ url: base, method: 'DELETE', done: 'Buchung gelöscht.', leave: true })}
              >
                Löschen
              </ConfirmButton>
            )}
          </div>
        </>
      )}
    </div>
  )

  const panel = (
    <SourcePanel
      bookingId={booking.id}
      sources={sources}
      locations={locations}
      proposalLocations={proposalLocations}
      sourceId={sourceId}
      onSourceChange={setSourceId}
      active={active}
      onMarkClick={(mark) => {
        show(mark)
        if (mark.scope === 'current') {
          setTab('data')
          // After the tab switch on narrow screens the field is visible again.
          setTimeout(() => focusField(mark.field))
        }
      }}
    />
  )

  return (
    <div className="space-y-6">
      {reviewing ? (
        <ReviewBar
          position={index >= 0 ? index + 1 : null}
          total={queue.data?.total ?? 0}
          onPrevious={previous ? () => go(reviewPath(previous.id, filters)) : undefined}
          onNext={next ? () => go(reviewPath(next.id, filters)) : undefined}
          onList={() => go(listPath(filters))}
        />
      ) : (
        <Link to={back} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeftIcon className="size-4" /> {backLabel(back)}
        </Link>
      )}
      <Header booking={booking} />
      {booking.canEdit && booking.reviewState !== 'NONE' && (
        <ReviewPanel
          booking={booking}
          onChanged={(result) => {
            // In the review mode every decision moves on; otherwise only a discarded booking leaves.
            if (reviewing) void moveOn(booking)
            else if (!result) navigate(back)
          }}
          onShowProposal={(field) => {
            show({ field, scope: 'proposal' })
            setTab('source')
          }}
        />
      )}
      {withSources ? (
        <Tabs value={tab} onValueChange={setTab} className="gap-4">
          <TabsList className="@5xl:hidden">
            <TabsTrigger value="data">Daten</TabsTrigger>
            <TabsTrigger value="source">Quelle</TabsTrigger>
          </TabsList>
          <div className="grid grid-cols-[minmax(0,1fr)] gap-8 @5xl:grid-cols-2">
            <TabsContent value="data" forceMount className="data-[state=inactive]:hidden @5xl:data-[state=inactive]:block">
              {form}
            </TabsContent>
            <TabsContent value="source" forceMount className="data-[state=inactive]:hidden @5xl:data-[state=inactive]:block @5xl:sticky @5xl:top-4 @5xl:self-start">
              {panel}
            </TabsContent>
          </div>
        </Tabs>
      ) : (
        <div className="max-w-2xl">{form}</div>
      )}
      <Dialog open={leaveTo !== null} onOpenChange={(open) => !open && setLeaveTo(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Ungespeicherte Änderungen verwerfen?</DialogTitle>
            <DialogDescription>Die Änderungen an dieser Buchung sind noch nicht gespeichert.</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setLeaveTo(null)}>
              Abbrechen
            </Button>
            <Button
              onClick={() => {
                const to = leaveTo!
                setLeaveTo(null)
                navigate(to)
              }}
            >
              Verwerfen
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
