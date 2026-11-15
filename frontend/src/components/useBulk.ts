import { useState } from 'react'
import { toast } from 'sonner'
import { bulkSummary, runBulk } from '@/lib/bulk'
import type { Selection } from './useSelection'

/**
 * Runs a bulk action over the selected items, reports the summary, keeps the failed items selected and
 * refreshes the page's data.
 */
export function useBulk(selection: Selection, refresh: () => Promise<unknown> | void) {
  const [busy, setBusy] = useState(false)

  async function run<T>(items: T[], getId: (item: T) => string, action: (item: T) => Promise<unknown>, verb: string) {
    setBusy(true)
    try {
      const result = await runBulk(items, action)
      const failed = result.failed.flatMap((f) => f.items.map(getId))
      selection.keepOnly(failed)
      await refresh()
      const text = bulkSummary(result, verb)
      if (failed.length) toast.warning(text)
      else toast.success(text)
      return result
    } finally {
      setBusy(false)
    }
  }

  return { busy, run }
}
