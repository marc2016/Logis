import type { ComponentType } from 'react'
import type { LucideIcon } from 'lucide-react'
import type { RouteObject } from 'react-router'

/** Contract that every frontend module fulfills (see design.md D11). */
export interface LogisModule {
  /** Identical to the module ID in the backend. */
  id: string
  label: string
  icon: LucideIcon
  order: number
  /** URL segment under which the module pages live, e.g. "ferienvermietung". */
  basePath: string
  /** Navigation entries; `path` relative to basePath. */
  navItems: { label: string; path: string; icon?: LucideIcon }[]
  /** Routes relative to basePath. */
  routes: RouteObject[]
  /** Labels for the results and kinds this module's mail handlers report in the core inbox. */
  mailResultLabels?: Record<string, string>
  mailKindLabels?: Record<string, string>
  /** Extra actions in the core inbox dialog for mails this module handled (handler key starts with the module id). */
  MailActions?: ComponentType<{ item: MailInboxItem; onDone: () => void }>
}

/** The inbox item as the core sends it (see core/mail/api.ts). */
export interface MailInboxItem {
  id: number
  handler: string | null
  result: string
  source: string | null
  subjectName: string | null
  linkId: number | null
  reference: string | null
}
