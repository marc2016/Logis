import type { MonthFigures, UnitTotal } from './api'

function daysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate()
}

function isLeap(year: number) {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0
}

/** Rounds half up like the backend's occupancy rate (BigDecimal HALF_UP). */
function rate(nights: number, days: number) {
  return days === 0 ? 0 : Math.floor((nights * 100) / days + 0.5)
}

/** Owner-use nights as a share of the month, rounded like the occupancy rate. */
export function ownerRate(f: MonthFigures, year: number) {
  return rate(f.ownerNights, daysInMonth(year, f.month))
}

function sum(values: string[]) {
  return (values.reduce((total, v) => total + Math.round(Number(v) * 100), 0) / 100).toFixed(2)
}

/**
 * Total of „Alle Einheiten“: nights and money summed; occupancy = nights / (days of the year × units), rounded
 * half up like the per-unit rate (design D4 of occupancy-charts).
 */
export function unitsTotal(rows: UnitTotal[], year: number): MonthFigures {
  const nights = rows.reduce((n, r) => n + r.total.nights, 0)
  return {
    month: 0,
    nights,
    occupancy: rate(nights, (isLeap(year) ? 366 : 365) * rows.length),
    ownerNights: rows.reduce((n, r) => n + r.total.ownerNights, 0),
    settledNet: sum(rows.map((r) => r.total.settledNet)),
    plannedRent: sum(rows.map((r) => r.total.plannedRent)),
  }
}

/**
 * Months of several units added up: nights and money summed, occupancy over the days of the month × units, rounded
 * like the per-unit rate. Used by the start page tiles for „Alle Einheiten“.
 */
export function monthsTotal(perUnit: MonthFigures[][], year: number): MonthFigures[] {
  return Array.from({ length: 12 }, (_, i) => {
    const months = perUnit.map((m) => m[i])
    const nights = months.reduce((n, m) => n + m.nights, 0)
    return {
      month: i + 1,
      nights,
      occupancy: rate(nights, daysInMonth(year, i + 1) * perUnit.length),
      ownerNights: months.reduce((n, m) => n + m.ownerNights, 0),
      settledNet: sum(months.map((m) => m.settledNet)),
      plannedRent: sum(months.map((m) => m.plannedRent)),
    }
  })
}

/** Settled net of the months 1 to `month`, e.g. this year up to today. */
export function netUpTo(months: MonthFigures[], month: number): string {
  return sum(months.filter((m) => m.month <= month).map((m) => m.settledNet))
}
