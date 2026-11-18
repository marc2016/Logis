import type * as React from 'react'
import { DayPicker } from 'react-day-picker'
import { de } from 'react-day-picker/locale/de'
import 'react-day-picker/style.css'
import { cn } from '@/lib/utils'

/**
 * Month calendar on react-day-picker with German labels and the app's colours. With
 * `captionLayout="dropdown"` month and year can be chosen directly.
 */
function Calendar({ className, style, ...props }: React.ComponentProps<typeof DayPicker>) {
  return (
    <DayPicker
      locale={de}
      className={cn('text-sm', className)}
      style={
        {
          '--rdp-accent-color': 'var(--primary)',
          '--rdp-accent-background-color': 'var(--accent)',
          '--rdp-day-height': '2.25rem',
          '--rdp-day-width': '2.25rem',
          '--rdp-day_button-height': '2.125rem',
          '--rdp-day_button-width': '2.125rem',
          ...style,
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { Calendar }
