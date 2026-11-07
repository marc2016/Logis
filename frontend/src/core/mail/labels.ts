import { useActiveModules } from '@/app/useActiveModules'
import { coreResultLabel, type InboxItem } from './api'

/** Labels for results and kinds, from the core and the active modules. */
export function useMailLabels() {
  const { modules } = useActiveModules()
  const results: Record<string, string> = { ...coreResultLabel }
  const kinds: Record<string, string> = {}
  modules.forEach((m) => {
    Object.assign(results, m.mailResultLabels ?? {})
    Object.assign(kinds, m.mailKindLabels ?? {})
  })
  return {
    result: (code: string) => results[code] ?? code,
    kind: (code: string | null) => (code ? (kinds[code] ?? code) : '–'),
    /** The active module that handled the mail, for its extra actions. */
    moduleOf: (item: InboxItem) => modules.find((m) => item.handler?.startsWith(`${m.id}.`)),
  }
}
