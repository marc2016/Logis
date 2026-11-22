import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ConfirmButton } from '@/components/ConfirmButton'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { fetchPushoverAccount, notificationsKey, pushoverAccountKey, type PushoverAccount } from './api'

/** The own Pushover key: stored encrypted on the server and never shown again. */
export function PushoverCard() {
  const queryClient = useQueryClient()
  const { data } = useQuery({ queryKey: pushoverAccountKey, queryFn: fetchPushoverAccount })
  const [userKey, setUserKey] = useState('')
  const [error, setError] = useState<string | null>(null)

  // Whether Pushover can reach the user decides which switches of „Benachrichtigungen“ can be used.
  async function refresh(result?: PushoverAccount) {
    if (result) queryClient.setQueryData(pushoverAccountKey, result)
    else await queryClient.invalidateQueries({ queryKey: pushoverAccountKey })
    await queryClient.invalidateQueries({ queryKey: notificationsKey })
  }

  const save = useMutation({
    mutationFn: () => api<PushoverAccount>('/api/account/pushover', { method: 'PUT', json: { userKey } }),
    onSuccess: async (result) => {
      setUserKey('')
      await refresh(result)
      toast.success('Pushover-Schlüssel gespeichert.')
    },
    onError: (err) => setError(errorMessage(err)),
  })
  const remove = useMutation({
    mutationFn: () => api('/api/account/pushover', { method: 'DELETE' }),
    onSuccess: async () => {
      await refresh()
      toast.success('Pushover-Schlüssel entfernt.')
    },
    onError: (err) => setError(errorMessage(err)),
  })
  const test = useMutation({
    mutationFn: () => api('/api/account/pushover/test', { method: 'POST' }),
    onSuccess: () => toast.success('Testnachricht gesendet.'),
    onError: async (err) => {
      setError(errorMessage(err))
      // The key may have turned out to be invalid.
      await refresh()
    },
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    save.mutate()
  }

  if (!data) return null
  const usable = data.hasKey && !data.invalid

  return (
    <Card>
      <CardHeader>
        <CardTitle>Pushover</CardTitle>
        <CardDescription>Mit deinem Pushover-Schlüssel schickt Logis Benachrichtigungen auf dein Handy.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {!data.available ? (
          <p className="text-sm text-muted-foreground">Pushover ist noch nicht eingerichtet. Bitte wende dich an einen Admin.</p>
        ) : (
          <>
            <p className="text-sm">
              {data.invalid ? (
                <span className="font-medium text-destructive">Der Pushover-Schlüssel ist nicht mehr gültig. Bitte hinterlege ihn neu.</span>
              ) : data.hasKey ? (
                <span className="font-medium">Ein Schlüssel ist hinterlegt.</span>
              ) : (
                <span className="text-muted-foreground">Kein Schlüssel hinterlegt</span>
              )}
            </p>
            <form onSubmit={submit} className="space-y-2">
              <Label htmlFor="pushover-user-key">{data.hasKey ? 'Neuer Pushover-Schlüssel' : 'Pushover-Schlüssel'}</Label>
              <div className="flex flex-wrap gap-2">
                <Input
                  id="pushover-user-key"
                  value={userKey}
                  onChange={(e) => setUserKey(e.target.value.trim())}
                  autoComplete="off"
                  spellCheck={false}
                  className="max-w-xs font-mono"
                  required
                />
                <Button type="submit" variant="outline" disabled={save.isPending}>
                  Speichern
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">Du findest ihn als „User Key“ in der Pushover-App und auf pushover.net.</p>
            </form>
            {data.hasKey && (
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  disabled={!usable || test.isPending}
                  onClick={() => {
                    setError(null)
                    test.mutate()
                  }}
                >
                  Testnachricht senden
                </Button>
                <ConfirmButton question="Schlüssel entfernen? Du bekommst dann keine Benachrichtigungen mehr über Pushover." onConfirm={() => remove.mutate()}>
                  Schlüssel entfernen
                </ConfirmButton>
              </div>
            )}
          </>
        )}
        <FormError message={error} />
      </CardContent>
    </Card>
  )
}
