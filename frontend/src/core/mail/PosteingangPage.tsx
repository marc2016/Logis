import { useState } from 'react'
import { createColumnHelper } from '@tanstack/react-table'
import { Badge } from '@/components/ui/badge'
import { DataTable } from '@/components/DataTable'
import { PageHeader } from '@/components/PageHeader'
import { InboxDialog } from './InboxDialog'
import { MailboxCard } from './MailboxCard'
import { ATTENTION_RESULTS, useInbox, useMailbox, type InboxItem } from './api'
import { useMailLabels } from './labels'

const dateTime = new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'short' })
const column = createColumnHelper<InboxItem>()

function useColumns() {
  const labels = useMailLabels()
  return [
  column.accessor('receivedAt', { header: 'Eingang', cell: (c) => dateTime.format(new Date(c.getValue())), meta: { label: 'Eingang' } }),
  column.accessor('source', {
    header: 'Absender',
    cell: (c) => c.getValue() ?? c.row.original.sender ?? '–',
    meta: { label: 'Absender', className: 'whitespace-normal break-all' },
  }),
  column.accessor('subject', {
    header: 'Betreff',
    cell: (c) => <span className="line-clamp-2">{c.getValue() ?? '–'}</span>,
    // Long subjects wrap instead of widening the page.
    meta: { label: 'Betreff', className: 'min-w-48 whitespace-normal break-words' },
  }),
  column.accessor('kind', { header: 'Art', cell: (c) => labels.kind(c.getValue()), meta: { label: 'Art' } }),
  column.accessor('result', {
    header: 'Ergebnis',
    cell: (c) => <Badge variant={ATTENTION_RESULTS.has(c.getValue()) ? 'default' : 'outline'}>{labels.result(c.getValue())}</Badge>,
    meta: { label: 'Ergebnis' },
  }),
  ]
}

/** Mailbox settings and the mails fetched from it, with what the handling part of Logis did. */
export function PosteingangPage() {
  const { data: mailbox } = useMailbox()
  const { data: inbox = [], isLoading } = useInbox()
  const [open, setOpen] = useState<InboxItem | null>(null)
  const columns = useColumns()

  return (
    <>
      <PageHeader title="Posteingang" description="Mails aus deinem Postfach, automatisch gelesen und an die passenden Bereiche übergeben." />
      {mailbox && <MailboxCard key={String(mailbox.configured)} mailbox={mailbox} />}
      <DataTable
        tableId="mail.inbox"
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
