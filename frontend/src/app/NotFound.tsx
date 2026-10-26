import { Link } from 'react-router'
import { Button } from '@/components/ui/button'

export function NotFound() {
  return (
    <div className="flex flex-col items-start gap-4 py-12">
      <h1 className="text-2xl font-semibold">Seite nicht gefunden</h1>
      <p className="text-muted-foreground">Diese Seite gibt es nicht oder sie ist für dich nicht verfügbar.</p>
      <Button asChild variant="outline">
        <Link to="/">Zur Übersicht</Link>
      </Button>
    </div>
  )
}
