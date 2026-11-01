import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'

// Amounts are always decimal strings ("-84.90"), see lib/money.ts.

export type ShareRole = 'OWNER' | 'EDITOR' | 'VIEWER'
export type LegalEntityKind = 'PRIVATE_PERSON' | 'GBR' | 'GMBH' | 'OTHER'
export type AccountKind = 'CHECKING' | 'SAVINGS' | 'CASH' | 'CREDIT_CARD' | 'OTHER'
export type CategoryKind = 'INCOME' | 'EXPENSE'

export const roleLabel: Record<ShareRole, string> = { OWNER: 'Owner', EDITOR: 'Bearbeiten', VIEWER: 'Lesen' }
export const legalEntityKindLabel: Record<LegalEntityKind, string> = {
  PRIVATE_PERSON: 'Privatperson',
  GBR: 'GbR',
  GMBH: 'GmbH',
  OTHER: 'Sonstige',
}
export const accountKindLabel: Record<AccountKind, string> = {
  CHECKING: 'Girokonto',
  SAVINGS: 'Tagesgeld / Sparen',
  CASH: 'Bargeld',
  CREDIT_CARD: 'Kreditkarte',
  OTHER: 'Sonstiges',
}

export interface LegalEntity {
  id: number
  name: string
  kind: LegalEntityKind
  taxNumber: string | null
  personal: boolean
  accountCount: number
}

export interface Account {
  id: number
  name: string
  kind: AccountKind
  currency: string
  iban: string | null
  openingBalance: string
  balance: string
  archived: boolean
  myRole: ShareRole
  legalEntity: { id: number; name: string; kind: LegalEntityKind }
}

export interface Category {
  id: number
  name: string
  kind: CategoryKind
  parentId: number | null
  moduleId: string | null
  archived: boolean
  children: Category[]
}

export interface Transaction {
  id: number
  accountId: number
  accountName: string
  bookingDate: string
  amount: string
  categoryId: number | null
  categoryName: string | null
  counterparty: string | null
  purpose: string | null
  note: string | null
  transferId: string | null
  counterAccountId: number | null
  counterAccountName: string | null
  version: number
}

export interface TransactionPage {
  items: Transaction[]
  page: number
  size: number
  totalItems: number
  income: string
  expense: string
}

export interface TransactionFilter {
  accountId?: number
  from?: string
  to?: string
  categoryId?: number
  uncategorized?: boolean
  q?: string
  page?: number
  size?: number
}

export interface ShareEntry {
  userId: number
  username: string
  displayName: string
  role: ShareRole
}

export interface Shares {
  myRole: ShareRole
  shares: ShareEntry[]
}

export interface DirectoryUser {
  id: number
  username: string
  displayName: string
}

export const ledgerKeys = {
  accounts: ['accounts'] as const,
  account: (id: number) => ['accounts', id] as const,
  legalEntities: ['legal-entities'] as const,
  categories: ['categories'] as const,
  adminCategories: ['admin-categories'] as const,
  transactions: ['transactions'] as const,
  shares: (type: string, id: number) => ['shares', type, id] as const,
}

export function transactionQuery(filter: TransactionFilter): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filter)) {
    if (value !== undefined && value !== '' && value !== false) params.set(key, String(value))
  }
  const query = params.toString()
  return `/api/transactions${query ? `?${query}` : ''}`
}

export function useAccounts(includeArchived = false) {
  return useQuery({
    queryKey: [...ledgerKeys.accounts, { includeArchived }],
    queryFn: () => api<Account[]>(`/api/accounts${includeArchived ? '?includeArchived=true' : ''}`),
  })
}

export function useAccount(id: number) {
  return useQuery({ queryKey: ledgerKeys.account(id), queryFn: () => api<Account>(`/api/accounts/${id}`) })
}

export function useLegalEntities() {
  return useQuery({ queryKey: ledgerKeys.legalEntities, queryFn: () => api<LegalEntity[]>('/api/legal-entities') })
}

export function useCategories() {
  return useQuery({
    queryKey: ledgerKeys.categories,
    queryFn: () => api<Category[]>('/api/categories'),
    staleTime: 5 * 60_000,
  })
}

export function useAdminCategories() {
  return useQuery({ queryKey: ledgerKeys.adminCategories, queryFn: () => api<Category[]>('/api/admin/categories') })
}

export function useTransactions(filter: TransactionFilter) {
  return useQuery({
    queryKey: [...ledgerKeys.transactions, filter],
    queryFn: () => api<TransactionPage>(transactionQuery(filter)),
    placeholderData: (previous) => previous,
  })
}

export function useShares(type: string, id: number, enabled = true) {
  return useQuery({
    queryKey: ledgerKeys.shares(type, id),
    queryFn: () => api<Shares>(`/api/shares/${type}/${id}`),
    enabled,
  })
}

export function useUserSearch(q: string) {
  const term = q.trim()
  return useQuery({
    queryKey: ['user-search', term],
    queryFn: () => api<DirectoryUser[]>(`/api/users/search?q=${encodeURIComponent(term)}`),
    enabled: term.length >= 2,
  })
}

/** After entries, balances, lists and possibly account details change. */
export function useInvalidateLedger() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ledgerKeys.accounts }),
      queryClient.invalidateQueries({ queryKey: ledgerKeys.transactions }),
      queryClient.invalidateQueries({ queryKey: ledgerKeys.legalEntities }),
    ])
}

/** Grouped by owner, in the order of the server's sorting. */
export function groupByLegalEntity(accounts: Account[]) {
  const groups = new Map<number, { name: string; accounts: Account[] }>()
  for (const account of accounts) {
    const group = groups.get(account.legalEntity.id) ?? { name: account.legalEntity.name, accounts: [] }
    group.accounts.push(account)
    groups.set(account.legalEntity.id, group)
  }
  return [...groups.entries()].map(([id, g]) => ({ id, ...g }))
}

/** Accounts in which the user may enter transactions. */
export function writableAccounts(accounts: Account[]) {
  return accounts.filter((a) => a.myRole !== 'VIEWER' && !a.archived)
}
