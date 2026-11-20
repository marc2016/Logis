import { ChevronLeftIcon, ChevronRightIcon, ListIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface Props {
  /** 1-based position of the open booking, or null if it is no longer in the queue. */
  position: number | null
  total: number
  onPrevious?: () => void
  onNext?: () => void
  onList: () => void
}

/** The bar of the review mode: where in the queue, and back and forth (design D5 of booking-review-queue). */
export function ReviewBar({ position, total, onPrevious, onNext, onList }: Props) {
  return (
    <nav aria-label="Prüfen" className="flex flex-wrap items-center gap-2 rounded-lg border bg-muted/40 p-2">
      <Button type="button" variant="ghost" size="sm" onClick={onList}>
        <ListIcon />
        Zur Liste
      </Button>
      <span className="ml-auto text-sm font-medium tabular-nums @md:mx-auto" aria-live="polite">
        Prüfen: {position ? `${position} von ${total}` : `${total} offen`}
      </span>
      {/* On phones the buttons share a row of their own, so both are easy to hit. */}
      <div className="flex w-full gap-2 @md:w-auto">
        <Button type="button" variant="outline" size="sm" className="min-h-10 flex-1" disabled={!onPrevious} onClick={onPrevious}>
          <ChevronLeftIcon />
          Zurück
        </Button>
        <Button type="button" variant="outline" size="sm" className="min-h-10 flex-1" disabled={!onNext} onClick={onNext}>
          Weiter
          <ChevronRightIcon />
        </Button>
      </div>
    </nav>
  )
}
