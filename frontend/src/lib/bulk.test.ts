import { describe, expect, it } from 'vitest'
import { ApiError } from './api'
import { bulkSummary, runBulk, SkipError } from './bulk'

describe('runBulk', () => {
  it('runs every item and reports them all as done', async () => {
    const seen: number[] = []
    const result = await runBulk([1, 2, 3], async (n) => void seen.push(n))
    expect(result.ok).toEqual([1, 2, 3])
    expect(result.failed).toEqual([])
    expect(seen.sort()).toEqual([1, 2, 3])
    expect(bulkSummary(result, 'gelöscht')).toBe('3 gelöscht')
  })

  it('groups failures by message and keeps going', async () => {
    const result = await runBulk([1, 2, 3, 4, 5], async (n) => {
      if (n === 2 || n === 4) throw new ApiError(409, 'Die Buchung ist abgerechnet und kann nur storniert werden')
      if (n === 5) throw new SkipError('Nicht zu prüfen')
    })
    expect(result.ok).toEqual([1, 3])
    expect(result.failed).toEqual([
      { message: 'Die Buchung ist abgerechnet und kann nur storniert werden', items: [2, 4] },
      { message: 'Nicht zu prüfen', items: [5] },
    ])
    expect(bulkSummary(result, 'gelöscht')).toBe(
      '2 gelöscht, 2 nicht: Die Buchung ist abgerechnet und kann nur storniert werden, 1 nicht: Nicht zu prüfen',
    )
  })

  it('runs at most four at a time', async () => {
    let running = 0
    let max = 0
    await runBulk(Array.from({ length: 10 }, (_, i) => i), async () => {
      running++
      max = Math.max(max, running)
      await new Promise((r) => setTimeout(r, 5))
      running--
    })
    expect(max).toBe(4)
  })
})
