import { useQueries } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { rentalKeys, useRentalUnits, useUnitsOverview, type MonthFigures, type Overview } from '../api'
import { monthsTotal } from '../occupancyFigures'

/**
 * The months of one unit or of all units with figures in the year, from the evaluation's own queries, so the start
 * page and the evaluation share their cache. `unavailable`: the chosen unit is no longer visible (design D6).
 */
export function useYearFigures(unitId: number | undefined, year: number, enabled = true) {
  const { data: units } = useRentalUnits()
  const unavailable = unitId !== undefined && units !== undefined && !units.some((u) => u.id === unitId)
  const all = useUnitsOverview(year, enabled && unitId === undefined)
  const ids = !enabled ? [] : unitId !== undefined ? (unavailable ? [] : [unitId]) : (all.data ?? []).map((r) => r.unitId)
  const results = useQueries({
    queries: ids.map((id) => ({
      queryKey: rentalKeys.overview(id, year),
      queryFn: () => api<Overview>(`/api/vacationrental/occupancy/overview?unitId=${id}&year=${year}`),
    })),
  })
  const loading = !enabled ? false : units === undefined || (unitId === undefined && all.isLoading) || results.some((r) => r.isLoading)
  let months: MonthFigures[] | undefined
  if (enabled && !loading && !unavailable && results.every((r) => r.data)) {
    // One unit: the backend's own figures; several: added up like „Alle Einheiten“ on the evaluation page.
    months = unitId !== undefined ? results[0]?.data?.months : monthsTotal(results.map((r) => r.data!.months), year)
  }
  return { months, loading, unavailable, unitCount: ids.length }
}
