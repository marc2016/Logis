import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ConfirmButton } from '@/components/ConfirmButton'
import { FormError } from '@/components/FormError'
import { PageHeader } from '@/components/PageHeader'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { notificationsKey, pushoverAccountKey } from '@/core/notifications/api'

/** The token itself is never returned, only whether one is set. */
export interface PushoverSettings {
  configured: boolean
  updatedAt: string | null
}

const pushoverKey = ['admin-pushover'] as const

/** „Pushover“: the application token Logis sends notifications with. */
export function PushoverPage() {
  const queryClient = useQueryClient()
  const { data: settings } = useQuery({ queryKey: pushoverKey, queryFn: () => api<PushoverSettings>('/api/admin/pushover') })
  const [token, setToken] = useState('')
  const [error, setError] = useState<string | null>(null)

  // The admin's own cards under „Mein Konto“ depend on whether a token is set.
  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: pushoverKey })
    await queryClient.invalidateQueries({ queryKey: pushoverAccountKey })
    await queryClient.invalidateQueries({ queryKey: notificationsKey })
  }

  const save = useMutation({
    mutationFn: () => api<PushoverSettings>('/api/admin/pushover', { method: 'PUT', json: { token } }),
    onSuccess: async () => {
      setToken('')
      await refresh()
      toast.success('Pushover-Token gespeichert und geprüft.')
    },
    onError: (err) => setError(errorMessage(err)),
  })
  const remove = useMutation({
    mutationFn: () => api('/api/admin/pushover', { method: 'DELETE' }),
    onSuccess: async () => {
      await refresh()
      toast.success('Token entfernt. Logis sendet nichts mehr über Pushover.')
    },
    onError: (err) => toast.error(errorMessage(err)),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    save.mutate()
  }

  return (
    <>
      <PageHeader title="Pushover" description="Über Pushover schickt Logis Benachrichtigungen auf die Handys der Benutzer." />
      <div className="grid max-w-3xl gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Anwendung</CardTitle>
            <CardDescription>
              {settings?.configured
                ? 'Ein Token ist hinterlegt. Es wird verschlüsselt gespeichert und nie angezeigt.'
                : 'Noch nicht eingerichtet: Logis sendet nichts über Pushover. Lege auf pushover.net eine Anwendung an und trage ihr API-Token hier ein.'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {settings && (
              <form onSubmit={submit} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="pushover-token">API-Token</Label>
                  <Input
                    id="pushover-token"
                    value={token}
                    onChange={(e) => setToken(e.target.value.trim())}
                    placeholder={settings.configured ? 'gespeichert – neues Token eintragen zum Ersetzen' : ''}
                    autoComplete="off"
                    spellCheck={false}
                    className="max-w-md font-mono"
                    required
                  />
                  <p className="text-xs text-muted-foreground">Beim Speichern prüft Logis das Token bei Pushover.</p>
                </div>
                <FormError message={error} />
                <div className="flex flex-wrap items-center gap-2">
                  <Button type="submit" disabled={save.isPending}>
                    Speichern
                  </Button>
                  {settings.configured && (
                    <ConfirmButton question="Token entfernen? Logis sendet dann nichts mehr über Pushover." onConfirm={() => remove.mutate()}>
                      Token entfernen
                    </ConfirmButton>
                  )}
                </div>
              </form>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Was an Pushover geht</CardTitle>
            <CardDescription>Pushover ist ein Dienst außerhalb von Logis mit Sitz in den USA.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <p>
              Logis sendet nur den Titel, einen kurzen Text und den Link einer Benachrichtigung: zum Beispiel Objekt, Zeitraum und Vermittler einer Buchung oder Browser und Ort einer
              Anmeldung.
            </p>
            <p>Namen und Kontaktdaten von Gästen, Beträge und Buchungsnummern werden nicht an Pushover gesendet. Sie stehen nur in Benachrichtigungen per E-Mail.</p>
            <p className="text-muted-foreground">
              Jeder Benutzer trägt unter „Mein Konto“ seinen eigenen Pushover-Schlüssel ein und wählt, worüber er benachrichtigt wird. Ohne Schlüssel wird ihm nichts gesendet.
            </p>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
