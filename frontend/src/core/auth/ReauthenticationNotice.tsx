import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ShieldAlertIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { rememberOnNextSignIn, sessionQueryKey } from './session'

/**
 * Shown instead of an admin page when the session was started by a remembered device: the admin area needs a
 * fresh sign-in. „Erneut anmelden“ ends the session, keeps the device remembered and returns here afterwards.
 */
export function ReauthenticationNotice() {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function reauthenticate() {
    setBusy(true)
    setError(null)
    try {
      await api('/api/auth/reauthenticate', { method: 'POST' })
      rememberOnNextSignIn()
      // The session is gone: the app shell sends the user to the sign-in and back here afterwards.
      await queryClient.invalidateQueries({ queryKey: sessionQueryKey })
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Card className="max-w-lg">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ShieldAlertIcon className="size-5" />
          Für den Admin-Bereich bitte erneut anmelden
        </CardTitle>
        <CardDescription>
          Dieses Gerät ist dauerhaft angemeldet. Für die Verwaltung ist zusätzlich eine frische Anmeldung mit Passwort und
          Code oder mit einem Passkey nötig.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <FormError message={error} />
        <Button onClick={reauthenticate} disabled={busy}>
          Erneut anmelden
        </Button>
      </CardContent>
    </Card>
  )
}
