import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { BulkActionBar } from '@/components/BulkActionBar'
import { ConfirmButton } from '@/components/ConfirmButton'
import { useSelection } from '@/components/useSelection'
import { useBulk } from '@/components/useBulk'
import { useActiveModules } from '@/app/useActiveModules'
import { api } from '@/lib/api'
import { SkipError } from '@/lib/bulk'
import { createColumnHelper } from '@tanstack/react-table'
import { Badge } from '@/components/ui/badge'
import { DataTable } from '@/components/DataTable'
import { PageHeader } from '@/components/PageHeader'
import { InboxDialog } from './InboxDialog'
import { MailboxCard } from './MailboxCard'
import { InboxFilters, type Option } from './InboxFilters'
import { DEFAULT_SORT, filterInbox, parseSort, senderOf, sortInbox, type InboxFilter } from './inboxFilter'
import { ATTENTION_RESULTS, useInbox, useInvalidateAfterMail, useMailbox, type InboxItem } from './api'
import { useMailLabels } from './labels'

/** URL parameter of each filter. */
const PARAM: Record<keyof InboxFilter, string> = { result: 'ergebnis', kind: 'art', sender: 'absender', from: 'von', to: 'bis', q: 'q' }

/** Distinct options sorted by label. */
function options(items: Option[]): Option[] {
  const byValue = new Map(items.map((o) => [o.value, o]))
  return [...byValue.values()].sort((a, b) => a.label.localeCompare(b.label, 'de'))
}

const dateTime = new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'short' })
const column = createColumnHelper<InboxItem>()

function useColumns() {
  const labels = useMailLabels()
  return [
  column.accessor('receivedAt', { header: 'Eingang', cell: (c) => dateTime.format(new Date(c.getValue())), meta: { label: 'Eingang', sortable: true } }),
  column.accessor('source', {
    header: 'Absender',
    cell: (c) => c.getValue() ?? c.row.original.sender ?? '–',
    meta: { label: 'Absender', sortable: true, className: 'whitespace-normal break-all' },
  }),
  column.accessor('subject', {
    header: 'Betreff',
    cell: (c) => <span className="line-clamp-2">{c.getValue() ?? '–'}</span>,
    // Long subjects wrap instead of widening the page.
    meta: { label: 'Betreff', sortable: true, className: 'min-w-48 whitespace-normal break-words' },
  }),
  column.accessor('kind', { header: 'Art', cell: (c) => labels.kind(c.getValue()), meta: { label: 'Art', sortable: true } }),
  column.accessor('result', {
    header: 'Ergebnis',
    cell: (c) => <Badge variant={ATTENTION_RESULTS.has(c.getValue()) ? 'default' : 'outline'}>{labels.result(c.getValue())}</Badge>,
    meta: { label: 'Ergebnis', sortable: true },
  }),
  ]
}

/** Mailbox settings and the mails fetched from it, with what the handling part of Logis did. */
export function PosteingangPage() {
  const { data: mailbox } = useMailbox()
  const { data: inbox = [], isLoading } = useInbox()
  const [open, setOpen] = useState<InboxItem | null>(null)
  const columns = useColumns()
  const labels = useMailLabels()
  const { modules } = useActiveModules()
  const [params, setParams] = useSearchParams()
  const filter: InboxFilter = {
    result: params.get('ergebnis') ?? '',
    kind: params.get('art') ?? '',
    sender: params.get('absender') ?? '',
    from: params.get('von') ?? '',
    to: params.get('bis') ?? '',
    q: params.get('q') ?? '',
  }
  const sort = parseSort(params.get('sort'))
  const visible = sortInbox(filterInbox(inbox, filter), sort, labels)
  // Filters and sorting live in the URL: any change there clears the selection, so bulk actions only see visible mails.
  const selection = useSelection(`inbox?${params.toString()}`)
  const invalidate = useInvalidateAfterMail()
  const bulk = useBulk(selection, invalidate)
  const selected = visible.filter((m) => selection.isSelected(String(m.id)))
  const mailId = (m: { id: number }) => String(m.id)

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    setParams(next, { replace: true })
  }
  const base = (m: { id: number }) => `/api/mail/inbox/${m.id}`

  return (
    <>
      <PageHeader title="Posteingang" description="Mails aus deinem Postfach, automatisch gelesen und an die passenden Bereiche übergeben." />
      {mailbox && <MailboxCard key={String(mailbox.configured)} mailbox={mailbox} />}
      <InboxFilters
        filter={filter}
        results={options(inbox.map((m) => ({ value: m.result, label: labels.result(m.result) })))}
        kinds={options(inbox.filter((m) => m.kind).map((m) => ({ value: m.kind!, label: labels.kind(m.kind) })))}
        senders={options(inbox.map((m) => ({ value: senderOf(m), label: senderOf(m) })))}
        onChange={(changes) => update(Object.fromEntries(Object.entries(changes).map(([k, v]) => [PARAM[k as keyof InboxFilter], v])))}
        onReset={() => setParams(params.get('sort') ? { sort: params.get('sort')! } : {}, { replace: true })}
      />
      <DataTable
        tableId="mail.inbox"
        columns={columns}
        data={visible}
        sort={sort}
        onSortChange={(s) => {
          const value = `${s.id},${s.desc ? 'desc' : 'asc'}`
          update({ sort: value === DEFAULT_SORT ? '' : value })
        }}
        getRowId={(m) => String(m.id)}
        onRowClick={setOpen}
        rowLabel={(m) => `Mail ${m.subject ?? ''}`}
        isLoading={isLoading}
        emptyText={inbox.length > 0 ? 'Keine Mails zu diesen Filtern.' : 'Noch keine Mails abgerufen.'}
        selection={selection}
      />
      <BulkActionBar selection={selection} busy={bulk.busy}>
        {modules.map((m) => {
          const Actions = m.BulkMailActions
          const own = selected.filter((mail) => mail.handler?.startsWith(`${m.id}.`))
          return Actions && own.length > 0 ? (
            <Actions key={m.id} items={own} busy={bulk.busy} run={(items, action, verb, concurrency) => bulk.run(items, mailId, action, verb, concurrency)} />
          ) : null
        })}
        <Button
          size="sm"
          variant="outline"
          disabled={bulk.busy}
          onClick={() =>
            bulk.run(selected, mailId, (m) => {
              if (!m.retryable) throw new SkipError('Die Mail kann nicht erneut verarbeitet werden')
              return api(`${base(m)}/retry`, { method: 'POST' })
            }, 'erneut verarbeitet')
          }
        >
          Erneut verarbeiten
        </Button>
        <Button size="sm" variant="outline" disabled={bulk.busy} onClick={() => bulk.run(selected, mailId, (m) => api(`${base(m)}/ignore`, { method: 'POST' }), 'ignoriert')}>
          Ignorieren
        </Button>
        <ConfirmButton
          question={`${selection.ids.size} ${selection.ids.size === 1 ? 'Eintrag' : 'Einträge'} löschen?`}
          disabled={bulk.busy}
          onConfirm={() => bulk.run(selected, mailId, (m) => api(base(m), { method: 'DELETE' }), 'gelöscht')}
        >
          Löschen
        </ConfirmButton>
      </BulkActionBar>
      {open && <InboxDialog item={open} onClose={() => setOpen(null)} />}
    </>
  )
}
