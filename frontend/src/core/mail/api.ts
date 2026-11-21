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
  /** The owner allows admins to export pseudonymized copies of their mails. */
  adminExportAllowed: boolean
  lastExport: { at: string; mailCount: number; adminName: string } | null
}

/** Progress of the current or last fetch of the mailbox; `total` is null until the folder has been searched. */
export interface FetchStatus {
  kind: 'POLL' | 'OLDER'
  from: string
  total: number | null
  checked: number
  created: number
  state: 'RUNNING' | 'DONE' | 'FAILED'
  error: string | null
  startedAt: string
  finishedAt: string | null
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
  /** The mail leaves something to do; the server decides, so the filter „Offen“ and notifications agree. */
  needsAttention: boolean
}

/** Results the core itself reports; modules add their own labels. */
export const coreResultLabel: Record<string, string> = {
  NOT_RECOGNISED: 'Nicht erkannt',
  IGNORED: 'Ignoriert',
  FAILED: 'Fehler',
}

export const mailKeys = {
  all: ['mail'] as const,
  mailbox: ['mail', 'mailbox'] as const,
  inbox: ['mail', 'inbox'] as const,
  fetchStatus: ['mail', 'fetch-status'] as const,
  text: (id: number) => ['mail', 'inbox', id, 'text'] as const,
}

export function useMailbox() {
  return useQuery({ queryKey: mailKeys.mailbox, queryFn: () => api<MailboxSettings>('/api/mail/mailbox') })
}

/** Refreshed every few seconds while a fetch runs, so new mails appear during a long fetch. */
export function useInbox() {
  const { data: status } = useFetchStatus()
  return useQuery({
    queryKey: mailKeys.inbox,
    queryFn: () => api<InboxItem[]>('/api/mail/inbox'),
    refetchInterval: status?.state === 'RUNNING' ? 4000 : false,
  })
}

/** Polled closely while a fetch runs; null if there was none since the backend started. */
export function useFetchStatus() {
  return useQuery({
    queryKey: mailKeys.fetchStatus,
    queryFn: async () => (await api<FetchStatus | undefined>('/api/mail/mailbox/fetch-status')) ?? null,
    // Slowly while idle, to notice the regular poll starting.
    refetchInterval: (query) => (query.state.data?.state === 'RUNNING' ? 1500 : 15000),
  })
}

/** Mail actions can create or change records of modules (e.g. bookings): refresh everything. */
export function useInvalidateAfterMail() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries()
}
