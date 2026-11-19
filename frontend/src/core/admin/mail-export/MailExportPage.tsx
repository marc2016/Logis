import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { DownloadIcon } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { DateField } from '@/components/DateField'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { api, apiDownload, saveBlob } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { useActiveModules } from '@/app/useActiveModules'
import { coreResultLabel } from '@/core/mail/api'

export interface ExportMailbox {
  ownerUserId: number
  username: string
  displayName: string
}

export interface ExportLogEntry {
  id: number
  at: string
  adminName: string
  ownerUserId: number
  ownerName: string
  mailCount: number
  filters: string
}

export const WARNING = 'Pseudonymisiert nach bestem Bemühen. Nicht erkannte Namen und Adressen können enthalten sein.'

const keys = {
  mailboxes: ['admin-mail-export', 'mailboxes'] as const,
  agencies: (owners: number[]) => ['admin-mail-export', 'agencies', owners] as const,
  log: ['admin-mail-export', 'log'] as const,
}

const dateTime = new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'short' })

/** The filters of a log entry in words, e.g. „01.11.2026 – 30.11.2026 · Nicht erkannt · Fewo & Meer“. */
function describeFilters(json: string, resultLabel: (code: string) => string) {
  try {
    const f = JSON.parse(json) as { from?: string; to?: string; results?: string[]; sources?: string[] }
    const parts: string[] = []
    const date = (iso: string) => iso.split('-').reverse().join('.')
    if (f.from || f.to) parts.push(`${f.from ? date(f.from) : '…'} – ${f.to ? date(f.to) : '…'}`)
    if (f.results?.length) parts.push(f.results.map(resultLabel).join(', '))
    if (f.sources?.length) parts.push(f.sources.join(', '))
    return parts.length ? parts.join(' · ') : 'Alle Mails'
  } catch {
    return '–'
  }
}

function toggle<T>(list: T[], value: T, on: boolean) {
  return on ? [...list, value] : list.filter((v) => v !== value)
}

/**
 * „Mail-Export“: pseudonymized mails of consenting mailboxes as a ZIP, for example to improve mail recognition.
 * Only mailboxes whose owners consented are listed; every export is logged.
 */
export function MailExportPage() {
  const queryClient = useQueryClient()
  const { modules } = useActiveModules()
  const resultLabels: Record<string, string> = { ...coreResultLabel }
  modules.forEach((m) => Object.assign(resultLabels, m.mailResultLabels ?? {}))
  const resultLabel = (code: string) => resultLabels[code] ?? code

  const { data: mailboxes, isLoading } = useQuery({
    queryKey: keys.mailboxes,
    queryFn: () => api<ExportMailbox[]>('/api/admin/mail-export/mailboxes'),
  })
  const { data: log = [] } = useQuery({ queryKey: keys.log, queryFn: () => api<ExportLogEntry[]>('/api/admin/mail-export/log') })

  // All consenting mailboxes are chosen until the admin changes it.
  const [chosen, setChosen] = useState<number[] | null>(null)
  const owners = chosen ?? (mailboxes ?? []).map((m) => m.ownerUserId)
  const { data: agencies = [] } = useQuery({
    queryKey: keys.agencies(owners),
    queryFn: () => api<string[]>(`/api/admin/mail-export/agencies?${owners.map((o) => `owners=${o}`).join('&')}`),
    enabled: owners.length > 0,
  })
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [results, setResults] = useState<string[]>([])
  const [sources, setSources] = useState<string[]>([])
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const download = useMutation({
    mutationFn: () =>
      apiDownload('/api/admin/mail-export', { owners, from: from || null, to: to || null, results, sources }),
    onSuccess: ({ blob, filename }) => {
      saveBlob(blob, filename ?? 'logis-mails.zip')
      toast.success('Export heruntergeladen.')
      void queryClient.invalidateQueries({ queryKey: keys.log })
    },
    onError: (err) => setError(errorMessage(err)),
    onSettled: () => setConfirming(false),
  })

  return (
    <>
      <PageHeader
        title="Mail-Export"
        description="Pseudonymisierte Mails aus freigegebenen Postfächern herunterladen, z. B. um die Erkennung neuer Mail-Formate zu verbessern."
      />
      <div className="grid max-w-4xl gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Export</CardTitle>
            <CardDescription>
              Nur Postfächer, deren Inhaber den Export erlaubt haben. Gelöschte Mails werden nie exportiert, Anhänge entfernt.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {!isLoading && mailboxes?.length === 0 ? (
              <p className="text-sm text-muted-foreground">Kein Postfach ist für den Export freigegeben.</p>
            ) : (
              <>
                <fieldset className="space-y-2">
                  <legend className="text-sm font-medium">Postfächer</legend>
                  {(mailboxes ?? []).map((m) => (
                    <label key={m.ownerUserId} className="flex items-center gap-2 text-sm">
                      <Checkbox
                        checked={owners.includes(m.ownerUserId)}
                        onCheckedChange={(on) => setChosen(toggle(owners, m.ownerUserId, on === true))}
                        aria-label={`Postfach von ${m.displayName}`}
                      />
                      {m.displayName} <span className="text-muted-foreground">({m.username})</span>
                    </label>
                  ))}
                </fieldset>
                <div className="flex flex-wrap gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="exp-from">Von</Label>
                    <DateField id="exp-from" label="Von" value={from} onChange={setFrom} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="exp-to">Bis</Label>
                    <DateField id="exp-to" label="Bis" value={to} onChange={setTo} />
                  </div>
                </div>
                <fieldset className="space-y-2">
                  <legend className="text-sm font-medium">Ergebnis</legend>
                  <div className="flex flex-wrap gap-x-4 gap-y-2">
                    {Object.keys(resultLabels).map((code) => (
                      <label key={code} className="flex items-center gap-2 text-sm">
                        <Checkbox
                          checked={results.includes(code)}
                          onCheckedChange={(on) => setResults(toggle(results, code, on === true))}
                          aria-label={resultLabel(code)}
                        />
                        {resultLabel(code)}
                      </label>
                    ))}
                  </div>
                  <p className="text-xs text-muted-foreground">Keine Auswahl: alle Ergebnisse.</p>
                </fieldset>
                {agencies.length > 0 && (
                  <fieldset className="space-y-2">
                    <legend className="text-sm font-medium">Vermittler</legend>
                    <div className="flex flex-wrap gap-x-4 gap-y-2">
                      {agencies.map((a) => (
                        <label key={a} className="flex items-center gap-2 text-sm">
                          <Checkbox
                            checked={sources.includes(a)}
                            onCheckedChange={(on) => setSources(toggle(sources, a, on === true))}
                            aria-label={a}
                          />
                          {a}
                        </label>
                      ))}
                    </div>
                    <p className="text-xs text-muted-foreground">Keine Auswahl: alle Vermittler.</p>
                  </fieldset>
                )}
                <FormError message={error} />
                <Button
                  disabled={owners.length === 0}
                  onClick={() => {
                    setError(null)
                    setConfirming(true)
                  }}
                >
                  <DownloadIcon />
                  Exportieren
                </Button>
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Protokoll</CardTitle>
            <CardDescription>Jeder Export wird je Postfach protokolliert. Die Inhaber sehen ihren letzten Export.</CardDescription>
          </CardHeader>
          <CardContent>
            {log.length === 0 ? (
              <p className="text-sm text-muted-foreground">Noch keine Exporte.</p>
            ) : (
              <div className="rounded-lg border">
                <Table aria-label="Protokoll">
                  <TableHeader>
                    <TableRow>
                      <TableHead>Zeitpunkt</TableHead>
                      <TableHead>Postfach</TableHead>
                      <TableHead className="text-right">Mails</TableHead>
                      <TableHead>Filter</TableHead>
                      <TableHead>Admin</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {log.map((e) => (
                      <TableRow key={e.id}>
                        <TableCell>{dateTime.format(new Date(e.at))}</TableCell>
                        <TableCell>{e.ownerName}</TableCell>
                        <TableCell className="text-right">{e.mailCount}</TableCell>
                        <TableCell>{describeFilters(e.filters, resultLabel)}</TableCell>
                        <TableCell>{e.adminName}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <Dialog open={confirming} onOpenChange={(open) => !open && !download.isPending && setConfirming(false)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mails exportieren?</DialogTitle>
            <DialogDescription>Die Mails werden als ZIP mit einer .eml-Datei je Mail und einer Übersicht heruntergeladen.</DialogDescription>
          </DialogHeader>
          <Alert>
            <AlertDescription>{WARNING}</AlertDescription>
          </Alert>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirming(false)} disabled={download.isPending}>
              Abbrechen
            </Button>
            <Button onClick={() => download.mutate()} disabled={download.isPending}>
              Herunterladen
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
