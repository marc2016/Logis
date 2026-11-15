import { errorMessage } from './errors'

export interface BulkFailure<T> {
  message: string
  items: T[]
}

export interface BulkResult<T> {
  ok: T[]
  failed: BulkFailure<T>[]
}

/** A failure decided before calling the server, e.g. an action that does not apply to an entry. */
export class SkipError extends Error {}

/**
 * Runs an action for every item on its own, a few at a time; a failing item does not stop the others.
 * Failures are grouped by their message, in the order they first occurred.
 */
export async function runBulk<T>(items: T[], action: (item: T) => Promise<unknown>, concurrency = 4): Promise<BulkResult<T>> {
  const ok: T[] = []
  const failures = new Map<string, T[]>()
  let next = 0
  async function worker() {
    while (next < items.length) {
      const item = items[next++]
      try {
        await action(item)
        ok.push(item)
      } catch (err) {
        const message = err instanceof SkipError ? err.message : errorMessage(err)
        failures.set(message, [...(failures.get(message) ?? []), item])
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, worker))
  // Keep the order of the input for the successful items.
  ok.sort((a, b) => items.indexOf(a) - items.indexOf(b))
  return { ok, failed: [...failures.entries()].map(([message, failed]) => ({ message, items: failed })) }
}

/** "8 gelöscht, 2 nicht: Die Buchung ist abgerechnet …" */
export function bulkSummary(result: BulkResult<unknown>, verb: string) {
  const parts = [`${result.ok.length} ${verb}`]
  result.failed.forEach((f) => parts.push(`${f.items.length} nicht: ${f.message}`))
  return parts.join(', ')
}
