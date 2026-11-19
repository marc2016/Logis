import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { DateField } from '@/components/DateField'
import { minusYears } from '@/components/dateInput'
import { todayIso } from '@/core/ledger/transactions/format'
import { mailKeys, useFetchStatus, type FetchStatus, type MailboxSettings } from './api'
import { FetchProgress } from './FetchProgress'

const RANGES = [
  { label: '1 Jahr', years: 1 },
  { label: '2 Jahre', years: 2 },
  { label: '5 Jahre', years: 5 },
]

const dateTime = new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'short' })

/** IMAP settings of the user's own mailbox; the password is never shown, only replaced. */
export function MailboxCard({ mailbox }: { mailbox: MailboxSettings }) {
  const queryClient = useQueryClient()
  const [host, setHost] = useState(mailbox.host ?? '')
  const [port, setPort] = useState(String(mailbox.port ?? 993))
  const [username, setUsername] = useState(mailbox.username ?? '')
  const [password, setPassword] = useState('')
  const [folder, setFolder] = useState(mailbox.folder ?? 'INBOX')
  const [useTls, setUseTls] = useState(mailbox.useTls ?? true)
  const [cutoffDate, setCutoffDate] = useState(mailbox.cutoffDate ?? todayIso())
  const [enabled, setEnabled] = useState(mailbox.enabled ?? true)
  const [olderFrom, setOlderFrom] = useState('')
  const today = todayIso()
  const [error, setError] = useState<string | null>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: mailKeys.all })

  const save = useMutation({
    mutationFn: () =>
      api<MailboxSettings>('/api/mail/mailbox', {
        method: 'PUT',
        json: { host, port: Number(port), username, password, folder, useTls, cutoffDate, enabled },
      }),
    onSuccess: async () => {
      setPassword('')
      await refresh()
      toast.success('Postfach gespeichert und erreichbar.')
    },
    onError: (err) => setError(errorMessage(err)),
  })
  const { data: status } = useFetchStatus()
  const trigger = useMutation({
    mutationFn: ({ url, body }: { url: string; body?: unknown }) =>
      api<FetchStatus | undefined>(url, { method: 'POST', json: body ?? {} }),
    onSuccess: (started) => {
      if (started) queryClient.setQueryData(mailKeys.fetchStatus, started)
      else toast.info('Der Abruf ist ausgeschaltet.')
    },
    onError: (err) => toast.error(errorMessage(err)),
  })
  const busy = trigger.isPending || status?.state === 'RUNNING'
  const consent = useMutation({
    mutationFn: (allowed: boolean) =>
      api<MailboxSettings>('/api/mail/mailbox/export-consent', { method: 'PUT', json: { allowed } }),
    onSuccess: async (saved) => {
      await refresh()
      toast.success(saved.adminExportAllowed ? 'Export durch Admins erlaubt.' : 'Export durch Admins nicht mehr erlaubt.')
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  // When a fetch ends, show its mails and the new time of the last fetch.
  const running = status?.state === 'RUNNING'
  const wasRunning = useRef(running)
  useEffect(() => {
    if (wasRunning.current && !running) void queryClient.invalidateQueries({ queryKey: mailKeys.all })
    wasRunning.current = running
  }, [running, queryClient])

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    save.mutate()
  }

  return (
    <Card className="mb-6">
      <CardHeader>
        <CardTitle>Postfach</CardTitle>
        <CardDescription>
          Logis liest Mails aus diesem IMAP-Postfach oder -Ordner, alle paar Minuten und ab dem Stichtag, z. B. Buchungsmails deiner Vermittler. Mails werden dort nicht verändert.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form onSubmit={submit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="mb-host">IMAP-Server</Label>
              <Input id="mb-host" value={host} onChange={(e) => setHost(e.target.value)} placeholder="imap.example.org" required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="mb-port">Port</Label>
              <Input id="mb-port" type="number" value={port} onChange={(e) => setPort(e.target.value)} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="mb-user">Benutzername</Label>
              <Input id="mb-user" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="off" required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="mb-password">Passwort</Label>
              <Input
                id="mb-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={mailbox.hasPassword ? 'gespeichert – leer lassen zum Behalten' : ''}
                autoComplete="new-password"
                required={!mailbox.hasPassword}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="mb-folder">Ordner</Label>
              <Input id="mb-folder" value={folder} onChange={(e) => setFolder(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="mb-cutoff">Stichtag</Label>
              <Input id="mb-cutoff" type="date" value={cutoffDate} onChange={(e) => setCutoffDate(e.target.value)} required />
            </div>
          </div>
          <div className="flex flex-wrap gap-6 text-sm">
            <label className="inline-flex items-center gap-2">
              <Checkbox checked={useTls} onCheckedChange={(v) => setUseTls(v === true)} aria-label="Verschlüsselt (TLS)" />
              Verschlüsselt (TLS)
            </label>
            <label className="inline-flex items-center gap-2">
              <Checkbox checked={enabled} onCheckedChange={(v) => setEnabled(v === true)} aria-label="Abruf aktiv" />
              Abruf aktiv
            </label>
          </div>
          <FormError message={error} />
          <Button type="submit" disabled={save.isPending}>
            Speichern
          </Button>
        </form>

        {mailbox.configured && (
          <div className="space-y-3 border-t pt-4 text-sm">
            <p className="text-muted-foreground">
              Letzter Abruf: {mailbox.lastPollAt ? dateTime.format(new Date(mailbox.lastPollAt)) : 'noch keiner'}
            </p>
            {mailbox.lastError && !status && <p className="text-destructive" role="alert">{mailbox.lastError}</p>}
            <FetchProgress status={status} />
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={busy}
              onClick={() => trigger.mutate({ url: '/api/mail/mailbox/poll' })}
            >
              Jetzt abrufen
            </Button>
            <div className="space-y-2">
              <Label htmlFor="mb-older">Ältere Mails ab</Label>
              <div className="flex flex-wrap items-start gap-2">
                <ToggleGroup
                  type="single"
                  variant="outline"
                  size="sm"
                  value={RANGES.find((r) => minusYears(today, r.years) === olderFrom)?.label ?? ''}
                  onValueChange={(label) => {
                    const range = RANGES.find((r) => r.label === label)
                    if (range) setOlderFrom(minusYears(today, range.years))
                  }}
                  aria-label="Zeitraum"
                >
                  {RANGES.map((r) => (
                    <ToggleGroupItem key={r.label} value={r.label}>
                      {r.label}
                    </ToggleGroupItem>
                  ))}
                </ToggleGroup>
                <DateField id="mb-older" label="Ältere Mails ab" value={olderFrom} onChange={setOlderFrom} max={today} />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={!olderFrom || busy}
                  onClick={() => trigger.mutate({ url: '/api/mail/mailbox/fetch-older', body: { from: olderFrom } })}
                >
                  Ältere Mails abrufen
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">Bereits abgerufene Mails werden übersprungen, es entstehen keine doppelten Einträge.</p>
            </div>
          </div>
        )}

        {mailbox.configured && (
          <div className="space-y-2 border-t pt-4 text-sm">
            <label className="inline-flex items-center gap-2">
              <Checkbox
                checked={mailbox.adminExportAllowed}
                disabled={consent.isPending}
                onCheckedChange={(v) => consent.mutate(v === true)}
                aria-label="Admins dürfen meine Mails pseudonymisiert exportieren"
              />
              Admins dürfen meine Mails pseudonymisiert exportieren
            </label>
            <p className="text-xs text-muted-foreground">
              Zum Beispiel, um die Erkennung neuer Mail-Formate zu verbessern. Namen, Adressen, Telefonnummern, E-Mail-Adressen und
              Buchungsnummern werden nach bestem Bemühen ersetzt, Anhänge entfernt. Jeder Export wird protokolliert.
            </p>
            {mailbox.lastExport && (
              <p className="text-muted-foreground">
                Zuletzt exportiert am {dateTime.format(new Date(mailbox.lastExport.at))} · {mailbox.lastExport.mailCount}{' '}
                {mailbox.lastExport.mailCount === 1 ? 'Mail' : 'Mails'} · von {mailbox.lastExport.adminName}
              </p>
            )}
          </div>
        )}

        <Alert>
          <AlertDescription>
            {mailbox.aiEnabled
              ? 'KI ist aktiv: Mails, die Logis nicht selbst erkennt, werden samt Gastdaten an Anthropic (Claude) gesendet und dort ausgelesen.'
              : 'KI ist ausgeschaltet: Mails, die Logis nicht selbst erkennt, bleiben als „Nicht erkannt“ im Posteingang.'}
          </AlertDescription>
        </Alert>
      </CardContent>
    </Card>
  )
}
