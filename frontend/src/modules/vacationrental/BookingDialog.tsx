import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { BookingForm } from './BookingForm'
import { bookingPath } from './BookingPage.paths'

export { BOOKING_DOCUMENT_TYPE } from './BookingForm'

interface Props {
  /** Preselects the unit (or the first unit of the property). */
  defaultUnitId?: number
  defaultPropertyId?: number
  onClose: () => void
}

/** Records a new booking; existing bookings open as their own page. */
export function BookingDialog({ defaultUnitId, defaultPropertyId, onClose }: Props) {
  const navigate = useNavigate()
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Buchung erfassen</DialogTitle>
        </DialogHeader>
        <BookingForm
          booking={null}
          defaultUnitId={defaultUnitId}
          defaultPropertyId={defaultPropertyId}
          onCancel={onClose}
          onSaved={({ saved, failed }) => {
            if (failed.length) {
              // The booking is saved: open it, so the documents can be tried again there.
              toast.error(`Die Buchung wurde gespeichert, aber nicht alle Dokumente: ${failed.join('; ')}`)
              navigate(bookingPath(saved.id))
              return
            }
            toast.success('Buchung erfasst.')
            onClose()
          }}
        />
      </DialogContent>
    </Dialog>
  )
}
