import { useState } from 'react'
import { createColumnHelper } from '@tanstack/react-table'
import { Badge } from '@/components/ui/badge'
import { DataTable } from '@/components/DataTable'
import { PageHeader } from '@/components/PageHeader'
import { InboxDialog } from '../InboxDialog'
import { MailboxCard } from '../MailboxCard'
import { importResultLabel, mailTypeLabel, useInbox, useMailbox, type ImportResult, type InboxItem } from '../api'

const dateTime = new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'short' })
const attention: ImportResult[] = ['UNASSIGNED_UNIT', 'NOT_MATCHED', 'PERIOD_TAKEN', 'NOT_RECOGNISED', 'FAILED']
const column = createColumnHelper<InboxItem>()

const columns = [
  column.accessor('receivedAt', { header: 'Eingang', cell: (c) => dateTime.format(new Date(c.getValue())), meta: { label: 'Eingang' } }),
  column.accessor('agency', { header: 'Vermittler', cell: (c) => c.getValue() ?? c.row.original.sender ?? '–', meta: { label: 'Vermittler' } }),
  column.accessor('subject', { header: 'Betreff', cell: (c) => <span className="line-clamp-2">{c.getValue() ?? '–'}</span>, meta: { label: 'Betreff' } }),
  column.accessor('mailType', { header: 'Art', cell: (c) => (c.getValue() ? mailTypeLabel[c.getValue()!] : '–'), meta: { label: 'Art' } }),
  column.accessor('result', {
    header: 'Ergebnis',
    cell: (c) => <Badge variant={attention.includes(c.getValue()) ? 'default' : 'outline'}>{importResultLabel[c.getValue()]}</Badge>,
    meta: { label: 'Ergebnis' },
  }),
]

/** Mailbox settings and the mails fetched from it, with what the import did. */
export function PosteingangPage() {
  const { data: mailbox } = useMailbox()
  const { data: inbox = [], isLoading } = useInbox()
  const [open, setOpen] = useState<InboxItem | null>(null)

  return (
    <>
      <PageHeader title="Posteingang" description="Buchungsmails der Vermittler, automatisch gelesen." />
      {mailbox && <MailboxCard key={String(mailbox.configured)} mailbox={mailbox} />}
      <DataTable
        tableId="vacationrental.inbox"
        columns={columns}
        data={inbox}
        getRowId={(m) => String(m.id)}
        onRowClick={setOpen}
        rowLabel={(m) => `Mail ${m.subject ?? ''}`}
        isLoading={isLoading}
        emptyText="Noch keine Mails abgerufen."
      />
      {open && <InboxDialog item={open} onClose={() => setOpen(null)} />}
    </>
  )
}
