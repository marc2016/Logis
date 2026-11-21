import { Badge } from '@/components/ui/badge'
import { stateLabel, type TenancyState } from './api'

export function StateBadge({ state }: { state: TenancyState }) {
  return <Badge variant={state === 'RUNNING' ? 'default' : state === 'FUTURE' ? 'outline' : 'secondary'}>{stateLabel[state]}</Badge>
}
