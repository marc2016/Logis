import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { unitUsageLabel, type UnitUsage } from './api'

export function UsageSelect({ id, value, onChange }: { id: string; value: UnitUsage; onChange: (u: UnitUsage) => void }) {
  return (
    <Select value={value} onValueChange={(v) => onChange(v as UnitUsage)}>
      <SelectTrigger id={id} className="w-full">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {(Object.keys(unitUsageLabel) as UnitUsage[]).map((u) => (
          <SelectItem key={u} value={u}>
            {unitUsageLabel[u]}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
