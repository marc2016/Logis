import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function Pagination({ page, size, total, onChange }: { page: number; size: number; total: number; onChange: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size))
  if (pages <= 1) return null
  return (
    <div className="mt-4 flex items-center justify-end gap-2 text-sm">
      <span className="text-muted-foreground">
        Seite {page + 1} von {pages}
      </span>
      <Button variant="outline" size="icon" aria-label="Vorherige Seite" disabled={page === 0} onClick={() => onChange(page - 1)}>
        <ChevronLeftIcon />
      </Button>
      <Button variant="outline" size="icon" aria-label="Nächste Seite" disabled={page + 1 >= pages} onClick={() => onChange(page + 1)}>
        <ChevronRightIcon />
      </Button>
    </div>
  )
}
