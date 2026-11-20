import { Link, Navigate, useSearchParams } from 'react-router'
import { CheckCircle2Icon } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import { listPath, queueParams, reviewPath, useReviewQueue } from '../reviewQueue'

/** „Prüfen“: opens the first booking to review, or says that all are done (design D3 of booking-review-queue). */
export function ReviewStartPage() {
  const [params] = useSearchParams()
  const filters = queueParams(params)
  const { data } = useReviewQueue(filters)
  if (!data) return <Skeleton className="h-8 w-64" />
  const first = data.items[0]
  if (first) return <Navigate to={reviewPath(first.id, filters)} replace />
  return (
    <>
      <PageHeader title="Prüfen" />
      <div className="flex flex-col items-start gap-3">
        <p className="flex items-center gap-2 text-lg font-medium">
          <CheckCircle2Icon className="size-5 text-emerald-700" aria-hidden />
          Alles geprüft.
        </p>
        <Link to={listPath(filters)} className="text-sm underline underline-offset-4">
          Zur Liste
        </Link>
      </div>
    </>
  )
}
