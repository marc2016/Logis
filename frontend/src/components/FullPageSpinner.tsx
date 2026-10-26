import { Loader2Icon } from 'lucide-react'

export function FullPageSpinner() {
  return (
    <div className="flex min-h-svh items-center justify-center" role="status" aria-label="Wird geladen">
      <Loader2Icon className="size-6 animate-spin text-muted-foreground" />
    </div>
  )
}
