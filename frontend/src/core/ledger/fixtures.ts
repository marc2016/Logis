import type { Account, Category, Transaction, TransactionPage } from './api'

export function account(overrides: Partial<Account> = {}): Account {
  return {
    id: 1,
    name: 'Hauptkonto',
    kind: 'CHECKING',
    currency: 'EUR',
    iban: null,
    openingBalance: '0.00',
    balance: '1250.00',
    archived: false,
    myRole: 'OWNER',
    legalEntity: { id: 10, name: 'Anna privat', kind: 'PRIVATE_PERSON' },
    ...overrides,
  }
}

export function transaction(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 100,
    accountId: 1,
    accountName: 'Hauptkonto',
    bookingDate: '2026-10-03',
    amount: '-84.90',
    categoryId: null,
    categoryName: null,
    counterparty: 'Edeka',
    purpose: null,
    note: null,
    transferId: null,
    counterAccountId: null,
    counterAccountName: null,
    version: 0,
    ...overrides,
  }
}

export function page(items: Transaction[], overrides: Partial<TransactionPage> = {}): TransactionPage {
  return { items, page: 0, size: 50, totalItems: items.length, income: '0.00', expense: '-84.90', ...overrides }
}

export const categories: Category[] = [
  {
    id: 5,
    name: 'Wohnen',
    kind: 'EXPENSE',
    parentId: null,
    moduleId: null,
    archived: false,
    children: [{ id: 6, name: 'Grundsteuer', kind: 'EXPENSE', parentId: 5, moduleId: null, archived: false, children: [] }],
  },
  { id: 7, name: 'Lebensmittel', kind: 'EXPENSE', parentId: null, moduleId: null, archived: false, children: [] },
  { id: 8, name: 'Gehalt', kind: 'INCOME', parentId: null, moduleId: null, archived: false, children: [] },
]
