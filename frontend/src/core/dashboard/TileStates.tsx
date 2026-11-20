/** Shown by a tile whose setting points to an object the user can no longer see (design D6). */
export function Unavailable() {
  return (
    <p className="text-sm text-muted-foreground">
      Nicht mehr verfügbar. Bitte in den Einstellungen eine andere Auswahl treffen.
    </p>
  )
}

export function Loading() {
  return <p className="text-sm text-muted-foreground">Wird geladen …</p>
}
