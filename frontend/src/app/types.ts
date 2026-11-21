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
  /** Extra bulk actions in the core inbox for the selected mails this module handled. */
  BulkMailActions?: ComponentType<{ items: MailInboxItem[]; busy: boolean; run: BulkMailRun }>
  /** Tiles this module offers on the start page (see core/dashboard). */
  dashboardWidgets?: DashboardWidget[]
  /** Counts this module adds to the start page tile „Zu erledigen“. */
  todoSources?: TodoSource[]
}

/** A tile type of the start page (design D2 of configurable-dashboard). */
export interface DashboardWidget<S extends object = Record<string, unknown>> {
  /** Stable and namespaced, e.g. 'core.todo' or 'vacationrental.occupancy'; stored with the user's tiles. */
  type: string
  /** German title, e.g. 'Belegung'. */
  title: string
  description: string
  defaultSettings: S
  Tile: ComponentType<{ settings: S }>
  Settings?: ComponentType<{ value: S; onChange: (settings: S) => void }>
  /** Spans two columns where there are two or more. */
  wide?: boolean
}

/** One line of „Zu erledigen“: a count that links to the list it was taken from. */
export interface TodoSource {
  label: string
  to: string
  query: () => { queryKey: readonly unknown[]; queryFn: () => Promise<number> }
}

/** Runs an action for each mail, reports the summary and refreshes the inbox (see core/mail). */
/** `concurrency` 1 runs the mails one after the other, e.g. when one call changes the state of the next. */
export type BulkMailRun = (items: MailInboxItem[], action: (item: MailInboxItem) => Promise<unknown>, verb: string, concurrency?: number) => Promise<unknown>

/** The inbox item as the core sends it (see core/mail/api.ts). */
export interface MailInboxItem {
  id: number
  handler: string | null
  result: string
  source: string | null
  subjectName: string | null
  linkType: string | null
  linkId: number | null
  reference: string | null
}
