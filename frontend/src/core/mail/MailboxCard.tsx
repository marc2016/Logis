import { useState, type FormEvent } from 'react'
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
import { formatDate, todayIso } from '@/core/ledger/transactions/format'
import { mailKeys, type MailboxSettings } from './api'

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
  const trigger = useMutation({
    mutationFn: ({ url, body }: { url: string; body?: unknown; done: string }) => api(url, { method: 'POST', json: body ?? {} }),
    onSuccess: (_, { done }) => {
      toast.success(done)
      window.setTimeout(refresh, 3000)
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

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
            {mailbox.lastError && <p className="text-destructive" role="alert">{mailbox.lastError}</p>}
            <div className="flex flex-wrap items-end gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={trigger.isPending}
                onClick={() => trigger.mutate({ url: '/api/mail/mailbox/poll', done: 'Abruf gestartet.' })}
              >
                Jetzt abrufen
              </Button>
              <div className="space-y-1">
                <Label htmlFor="mb-older" className="text-xs text-muted-foreground">
                  Ältere Mails ab
                </Label>
                <Input id="mb-older" type="date" value={olderFrom} onChange={(e) => setOlderFrom(e.target.value)} className="w-40" />
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={!olderFrom || trigger.isPending}
                onClick={() =>
                  trigger.mutate({
                    url: '/api/mail/mailbox/fetch-older',
                    body: { from: olderFrom },
                    done: `Mails ab ${formatDate(olderFrom)} werden abgerufen.`,
                  })
                }
              >
                Ältere Mails abrufen
              </Button>
            </div>
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
