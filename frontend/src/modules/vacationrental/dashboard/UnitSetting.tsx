import { ObjectChoice } from '@/core/dashboard/SettingsFields'
import { unitLabel, useRentalUnits } from '../api'

/** One vacation rental unit or all, for the start page tiles. */
export function UnitSetting({ id, value, onChange }: { id: string; value: number | undefined; onChange: (unitId: number | undefined) => void }) {
  const { data: units = [] } = useRentalUnits()
  return (
    <ObjectChoice id={id} label="Einheit" value={value} allLabel="Alle Einheiten" onChange={onChange}
      options={units.filter((u) => !u.archived).map((u) => ({ id: u.id, name: unitLabel(u) }))} />
  )
}
