import type { DashboardWidget } from '@/app/types'
import { BalancesSettingsForm, BalancesTile, type BalancesSettings } from './tiles/BalancesTile'
import { TodoTile } from './tiles/TodoTile'
import { TransactionsSettingsForm, TransactionsTile, type TransactionsSettings } from './tiles/TransactionsTile'

const todo: DashboardWidget = {
  type: 'core.todo',
  title: 'Zu erledigen',
  description: 'Mails und Buchungen, die auf dich warten.',
  defaultSettings: {},
  Tile: TodoTile,
}

const balances: DashboardWidget<BalancesSettings> = {
  type: 'core.balances',
  title: 'Kontostände',
  description: 'Stände aller oder ausgewählter Konten.',
  defaultSettings: {},
  Tile: BalancesTile,
  Settings: BalancesSettingsForm,
}

const transactions: DashboardWidget<TransactionsSettings> = {
  type: 'core.transactions',
  title: 'Letzte Transaktionen',
  description: 'Die neuesten Umsätze eines Kontos oder aller Konten.',
  defaultSettings: { count: 5 },
  Tile: TransactionsTile,
  Settings: TransactionsSettingsForm,
}

/** The core's tiles; modules add theirs through `LogisModule.dashboardWidgets`. */
export const coreWidgets = [todo, balances, transactions] as unknown as DashboardWidget[]
