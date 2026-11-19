import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ConfirmButton } from '@/components/ConfirmButton'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'

type Security = 'NONE' | 'STARTTLS' | 'SSL_TLS'

export interface SmtpSettings {
  configured: boolean
  host: string | null
  port: number | null
  security: Security | null
  username: string | null
  hasPassword: boolean
  fromAddress: string | null
  fromName: string | null
  updatedAt: string | null
}

const securityLabel: Record<Security, string> = { NONE: 'Keine', STARTTLS: 'STARTTLS', SSL_TLS: 'SSL/TLS' }
const defaultPort: Record<Security, number> = { NONE: 25, STARTTLS: 587, SSL_TLS: 465 }
const smtpKey = ['admin-smtp'] as const

function SettingsForm({ settings }: { settings: SmtpSettings }) {
  const queryClient = useQueryClient()
  const [host, setHost] = useState(settings.host ?? '')
  const [security, setSecurity] = useState<Security>(settings.security ?? 'STARTTLS')
  const [port, setPort] = useState(String(settings.port ?? defaultPort.STARTTLS))
  const [username, setUsername] = useState(settings.username ?? '')
  const [password, setPassword] = useState('')
  const [fromAddress, setFromAddress] = useState(settings.fromAddress ?? '')
  const [fromName, setFromName] = useState(settings.fromName ?? 'Logis')
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () =>
      api<SmtpSettings>('/api/admin/smtp', {
        method: 'PUT',
        json: { host, port: Number(port), security, username, password, fromAddress, fromName },
      }),
    onSuccess: async () => {
      setPassword('')
      await queryClient.invalidateQueries({ queryKey: smtpKey })
      toast.success('Mailserver gespeichert und erreichbar.')
    },
    onError: (err) => setError(errorMessage(err)),
  })
  const remove = useMutation({
    mutationFn: () => api('/api/admin/smtp', { method: 'DELETE' }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: smtpKey })
      toast.success('Konfiguration entfernt. Logis versendet keine Mails mehr.')
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    save.mutate()
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="smtp-host">Mailserver</Label>
          <Input id="smtp-host" value={host} onChange={(e) => setHost(e.target.value)} placeholder="smtp.example.org" required />
        </div>
        <div className="space-y-2">
          <Label htmlFor="smtp-security">Verschlüsselung</Label>
          <Select
            value={security}
            onValueChange={(v) => {
              const next = v as Security
              // Follow the usual port of the chosen encryption, unless a custom one was entered.
              if (Number(port) === defaultPort[security]) setPort(String(defaultPort[next]))
              setSecurity(next)
            }}
          >
            <SelectTrigger id="smtp-security" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(Object.keys(securityLabel) as Security[]).map((s) => (
                <SelectItem key={s} value={s}>
                  {securityLabel[s]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="smtp-port">Port</Label>
          <Input id="smtp-port" type="number" value={port} onChange={(e) => setPort(e.target.value)} required />
        </div>
        <div className="space-y-2">
          <Label htmlFor="smtp-user">Benutzername</Label>
          <Input id="smtp-user" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="off" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="smtp-password">Passwort</Label>
          <Input
            id="smtp-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder={settings.hasPassword ? 'gespeichert – leer lassen zum Behalten' : ''}
            autoComplete="new-password"
          />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="smtp-from">Absenderadresse</Label>
          <Input id="smtp-from" type="email" value={fromAddress} onChange={(e) => setFromAddress(e.target.value)} placeholder="logis@example.org" required />
        </div>
        <div className="space-y-2">
          <Label htmlFor="smtp-from-name">Absendername</Label>
          <Input id="smtp-from-name" value={fromName} onChange={(e) => setFromName(e.target.value)} maxLength={128} />
        </div>
      </div>
      <FormError message={error} />
      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" disabled={save.isPending}>
          Speichern
        </Button>
        {settings.configured && (
          <ConfirmButton question="Konfiguration entfernen? Logis versendet dann keine Mails mehr." onConfirm={() => remove.mutate()}>
            Konfiguration entfernen
          </ConfirmButton>
        )}
      </div>
    </form>
  )
}

function TestMail({ defaultTo }: { defaultTo: string }) {
  const [to, setTo] = useState(defaultTo)
  const [error, setError] = useState<string | null>(null)
  const send = useMutation({
    mutationFn: () => api('/api/admin/smtp/test', { json: { to } }),
    onSuccess: () => toast.success(`Testmail an ${to} gesendet.`),
    onError: (err) => setError(errorMessage(err)),
  })
  return (
    <form
      className="space-y-2"
      onSubmit={(e) => {
        e.preventDefault()
        setError(null)
        send.mutate()
      }}
    >
      <Label htmlFor="smtp-test-to">Testmail an</Label>
      <div className="flex flex-wrap gap-2">
        <Input id="smtp-test-to" type="email" value={to} onChange={(e) => setTo(e.target.value)} className="max-w-xs" required />
        <Button type="submit" variant="outline" disabled={send.isPending}>
          Testmail senden
        </Button>
      </div>
      <FormError message={error} />
    </form>
  )
}

/** „E-Mail-Versand“: the system's outgoing mail server. */
export function SmtpPage() {
  const { data: settings } = useQuery({ queryKey: smtpKey, queryFn: () => api<SmtpSettings>('/api/admin/smtp') })
  const { data: email } = useQuery({
    queryKey: ['account', 'email'],
    queryFn: () => api<{ email: string | null }>('/api/account/email'),
  })
  return (
    <>
      <PageHeader
        title="E-Mail-Versand"
        description="Über diesen Mailserver versendet Logis Mails, z. B. Bestätigungscodes und Sicherheitshinweise."
      />
      <div className="grid max-w-3xl gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Mailserver</CardTitle>
            <CardDescription>
              {settings?.configured
                ? 'Beim Speichern prüft Logis die Verbindung. Das Passwort wird verschlüsselt gespeichert und nie angezeigt.'
                : 'Noch nicht eingerichtet: Logis versendet keine Mails. Beim Speichern prüft Logis die Verbindung.'}
            </CardDescription>
          </CardHeader>
          <CardContent>{settings && <SettingsForm key={settings.updatedAt ?? 'new'} settings={settings} />}</CardContent>
        </Card>
        {settings?.configured && (
          <Card>
            <CardHeader>
              <CardTitle>Testmail</CardTitle>
              <CardDescription>Prüft, ob Mails über den eingerichteten Server ankommen.</CardDescription>
            </CardHeader>
            <CardContent>
              {/* Mounted again once the admin's own address is known, to prefill it. */}
              <TestMail key={email?.email ?? ''} defaultTo={email?.email ?? ''} />
            </CardContent>
          </Card>
        )}
      </div>
    </>
  )
}
