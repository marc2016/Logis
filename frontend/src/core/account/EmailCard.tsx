import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FormError } from '@/components/FormError'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { sessionQueryKey } from '@/core/auth/session'

export interface AccountEmail {
  email: string | null
  pendingEmail: string | null
  canChange: boolean
}

const emailKey = ['account', 'email'] as const

/** The own e-mail address; a change is confirmed with a code sent to the new address. */
export function EmailCard() {
  const queryClient = useQueryClient()
  const { data } = useQuery({ queryKey: emailKey, queryFn: () => api<AccountEmail>('/api/account/email') })
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  // „Andere Adresse“ shows the address form again while a code is pending.
  const [otherAddress, setOtherAddress] = useState(false)
  const pending = otherAddress ? null : (data?.pendingEmail ?? null)

  const request = useMutation({
    mutationFn: (address: string) => api('/api/account/email', { json: { email: address } }),
    onSuccess: async (_, address) => {
      setCode('')
      setOtherAddress(false)
      await queryClient.invalidateQueries({ queryKey: emailKey })
      toast.success(`Code an ${address} gesendet.`)
    },
    onError: (err) => setError(errorMessage(err)),
  })
  const confirm = useMutation({
    mutationFn: () => api<AccountEmail>('/api/account/email/confirm', { json: { code } }),
    onSuccess: async (result) => {
      setEmail('')
      setCode('')
      queryClient.setQueryData(emailKey, result)
      await queryClient.invalidateQueries({ queryKey: sessionQueryKey })
      toast.success('E-Mail-Adresse geändert.')
    },
    onError: (err) => {
      setCode('')
      setError(errorMessage(err))
    },
  })

  function submitAddress(e: FormEvent) {
    e.preventDefault()
    setError(null)
    request.mutate(email)
  }

  function submitCode(e: FormEvent) {
    e.preventDefault()
    setError(null)
    confirm.mutate()
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>E-Mail-Adresse</CardTitle>
        <CardDescription>An diese Adresse schickt Logis Sicherheitshinweise, z. B. bei einer Anmeldung von einem neuen Gerät.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm">
          {!data ? null : data.email ? <span className="font-medium">{data.email}</span> : <span className="text-muted-foreground">Keine Adresse hinterlegt</span>}
        </p>
        {data && !data.canChange && (
          <p className="text-sm text-muted-foreground">Der E-Mail-Versand ist nicht eingerichtet. Bitte wende dich an einen Admin.</p>
        )}
        {data?.canChange && !pending && (
          <form onSubmit={submitAddress} className="space-y-2">
            <Label htmlFor="account-email">Neue E-Mail-Adresse</Label>
            <div className="flex flex-wrap gap-2">
              <Input id="account-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="max-w-xs" required />
              <Button type="submit" variant="outline" disabled={request.isPending}>
                Code senden
              </Button>
            </div>
          </form>
        )}
        {data?.canChange && pending && (
          <form onSubmit={submitCode} className="space-y-2">
            <p className="text-sm">
              Wir haben einen Code an <span className="font-medium">{pending}</span> gesendet. Er gilt 30 Minuten.
            </p>
            <Label htmlFor="account-email-code">Bestätigungscode</Label>
            <div className="flex flex-wrap gap-2">
              <Input
                id="account-email-code"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                className="max-w-32"
                required
              />
              <Button type="submit" disabled={confirm.isPending || code.length !== 6}>
                Bestätigen
              </Button>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="link" className="px-0" disabled={request.isPending} onClick={() => request.mutate(pending)}>
                Code erneut senden
              </Button>
              <Button type="button" variant="link" className="px-0" onClick={() => setOtherAddress(true)}>
                Andere Adresse
              </Button>
            </div>
          </form>
        )}
        <FormError message={error} />
      </CardContent>
    </Card>
  )
}
