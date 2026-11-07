import { useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'

interface Props {
  /** Button text, e.g. "Löschen". */
  children: ReactNode
  /** Question shown before the action runs, e.g. "Buchung wirklich löschen?". */
  question: string
  confirmLabel?: string
  onConfirm: () => void
  disabled?: boolean
  variant?: 'outline' | 'destructive' | 'ghost'
}

/** Two-step button for actions that cannot be undone, without a second dialog on top of the first. */
export function ConfirmButton({ children, question, confirmLabel = 'Ja', onConfirm, disabled, variant = 'outline' }: Props) {
  const [asking, setAsking] = useState(false)
  if (!asking) {
    return (
      <Button type="button" variant={variant} size="sm" disabled={disabled} onClick={() => setAsking(true)}>
        {children}
      </Button>
    )
  }
  return (
    <span className="inline-flex flex-wrap items-center gap-2 text-sm" role="group" aria-label={question}>
      <span>{question}</span>
      <Button
        type="button"
        variant="destructive"
        size="sm"
        disabled={disabled}
        onClick={() => {
          setAsking(false)
          onConfirm()
        }}
      >
        {confirmLabel}
      </Button>
      <Button type="button" variant="outline" size="sm" onClick={() => setAsking(false)}>
        Abbrechen
      </Button>
    </span>
  )
}
