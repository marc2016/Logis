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
