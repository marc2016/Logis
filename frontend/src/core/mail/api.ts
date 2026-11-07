import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'

export interface MailboxSettings {
  configured: boolean
  host: string | null
  port: number | null
  username: string | null
  hasPassword: boolean
  folder: string | null
  useTls: boolean | null
  cutoffDate: string | null
  enabled: boolean | null
  lastPollAt: string | null
  lastError: string | null
  aiEnabled: boolean
}

/** A fetched mail and what the handling part of Logis reported. */
export interface InboxItem {
  id: number
  receivedAt: string
  sender: string | null
  subject: string | null
  /** e.g. "vacationrental.booking"; null if no part of Logis took the mail. */
  handler: string | null
  kind: string | null
  source: string | null
  reference: string | null
  subjectName: string | null
  parsedBy: 'RULE' | 'AI' | null
  result: string
  resultDetail: string | null
  linkType: string | null
  linkId: number | null
  retryable: boolean
}

/** Results the core itself reports; modules add their own labels. */
export const coreResultLabel: Record<string, string> = {
  NOT_RECOGNISED: 'Nicht erkannt',
  IGNORED: 'Ignoriert',
  FAILED: 'Fehler',
}

/** Results that need the user's attention. */
export const ATTENTION_RESULTS = new Set(['NOT_RECOGNISED', 'FAILED', 'UNASSIGNED_UNIT', 'NOT_MATCHED', 'PERIOD_TAKEN'])

export const mailKeys = {
  all: ['mail'] as const,
  mailbox: ['mail', 'mailbox'] as const,
  inbox: ['mail', 'inbox'] as const,
  text: (id: number) => ['mail', 'inbox', id, 'text'] as const,
}

export function useMailbox() {
  return useQuery({ queryKey: mailKeys.mailbox, queryFn: () => api<MailboxSettings>('/api/mail/mailbox') })
}

export function useInbox() {
  return useQuery({ queryKey: mailKeys.inbox, queryFn: () => api<InboxItem[]>('/api/mail/inbox') })
}

/** Mail actions can create or change records of modules (e.g. bookings): refresh everything. */
export function useInvalidateAfterMail() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries()
}
