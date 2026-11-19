import { lazy, Suspense, useState } from 'react'
import { CalendarIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { formatDate } from '@/core/ledger/transactions/format'
import { fromIso, parseGermanDate, toIso } from './dateInput'

// Loaded when the calendar is opened first, to keep pages with a date field light.
const Calendar = lazy(() => import('@/components/ui/calendar').then((m) => ({ default: m.Calendar })))

export const INVALID_DATE = 'Bitte ein gültiges Datum eingeben.'
export const FUTURE_DATE = 'Das Datum darf nicht in der Zukunft liegen.'

interface Props {
  id: string
  /** ISO date or '' when nothing valid is chosen. */
  value: string
  onChange: (iso: string) => void
  /** Latest allowed ISO date, e.g. today. */
  max?: string
  /** Names the calendar button, e.g. the field's label. */
  label: string
  className?: string
}

/**
 * Date as typed text `TT.MM.JJJJ` with a calendar whose month and year can be chosen directly. Reports a valid
 * date as soon as it is complete and '' otherwise; tells why once the field is left.
 */
export function DateField({ id, value, onChange, max, label, className }: Props) {
  const [text, setText] = useState(value ? formatDate(value) : '')
  const [shown, setShown] = useState(value)
  const [touched, setTouched] = useState(false)
  const [open, setOpen] = useState(false)

  // A value set from outside (e.g. a quick range) replaces the typed text.
  if (value !== shown) {
    setShown(value)
    if (value) {
      setText(formatDate(value))
      setTouched(false)
    } else if (parseGermanDate(text)) {
      // Cleared from outside, e.g. by resetting filters.
      setText('')
    }
  }

  const parsed = parseGermanDate(text)
  const error = !text ? null : !parsed ? INVALID_DATE : max && parsed > max ? FUTURE_DATE : null

  function change(next: string) {
    setText(next)
    const iso = parseGermanDate(next)
    const valid = iso && !(max && iso > max) ? iso : ''
    setShown(valid)
    onChange(valid)
  }

  // A complete date in the future is reported at once, everything else when the field is left.
  const visibleError = error && (touched || error === FUTURE_DATE) ? error : null
  const selected = parsed ? fromIso(parsed) : undefined
  const maxDate = max ? fromIso(max) : undefined

  return (
    <div className={className}>
      <div className="flex w-full max-w-44 gap-1">
        <Input
          id={id}
          value={text}
          inputMode="numeric"
          placeholder="TT.MM.JJJJ"
          aria-invalid={visibleError ? true : undefined}
          aria-describedby={visibleError ? `${id}-error` : undefined}
          onChange={(e) => change(e.target.value)}
          onBlur={() => setTouched(true)}
          className="min-w-0 flex-1"
        />
        <Popover open={open} onOpenChange={setOpen}>
          <PopoverTrigger asChild>
            <Button type="button" variant="outline" size="icon" aria-label={`Kalender öffnen: ${label}`}>
              <CalendarIcon />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-auto" align="start">
            <Suspense fallback={<div className="size-64" />}>
              <Calendar
                mode="single"
                captionLayout="dropdown"
                selected={selected}
                defaultMonth={selected ?? maxDate}
                startMonth={new Date(2000, 0)}
                endMonth={maxDate ?? new Date(2100, 11)}
                disabled={maxDate ? { after: maxDate } : undefined}
                onSelect={(day) => {
                  if (!day) return
                  change(formatDate(toIso(day)))
                  setTouched(false)
                  setOpen(false)
                }}
              />
            </Suspense>
          </PopoverContent>
        </Popover>
      </div>
      {visibleError && (
        <p id={`${id}-error`} className="mt-1 text-xs text-destructive" role="alert">
          {visibleError}
        </p>
      )}
    </div>
  )
}
