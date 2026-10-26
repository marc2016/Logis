import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { QRCodeSVG } from 'qrcode.react'
import { LandmarkIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/components/ui/input-otp'
import { Label } from '@/components/ui/label'
import { FormError } from '@/components/FormError'
import { FullPageSpinner } from '@/components/FullPageSpinner'
import { RecoveryCodes } from '@/components/RecoveryCodes'
import { api, ApiError } from '@/lib/api'
import { safeNext, sessionQueryKey, useSession, type AuthStage, type SessionResponse } from './session'

type Step = AuthStage | 'SHOW_RECOVERY_CODES'

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Ein unerwarteter Fehler ist aufgetreten.'
}

/**
 * Mehrstufige Anmeldung: Passwort -> TOTP-Code bzw. Einrichtung -> ggf. Wiederherstellungscodes
 * und Passwortwechsel. Der Parameter `next` bleibt ueber alle Schritte erhalten.
 */
export function LoginPage() {
  const { data: session, isLoading } = useSession()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [step, setStep] = useState<Step | null>(null)
  const [stageAfterCodes, setStageAfterCodes] = useState<AuthStage>('AUTHENTICATED')
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([])

  const current: Step = step ?? session?.stage ?? 'ANONYMOUS'
  const restart = useCallback(() => setStep('ANONYMOUS'), [])

  useEffect(() => {
    if (current === 'AUTHENTICATED') {
      queryClient.invalidateQueries({ queryKey: sessionQueryKey }).then(() => navigate(next, { replace: true }))
    }
  }, [current, next, navigate, queryClient])

  function advance(response: SessionResponse) {
    if (response.recoveryCodes?.length) {
      setRecoveryCodes(response.recoveryCodes)
      setStageAfterCodes(response.stage)
      setStep('SHOW_RECOVERY_CODES')
    } else {
      setStep(response.stage)
    }
  }

  if (isLoading || current === 'AUTHENTICATED') return <FullPageSpinner />

  return (
    <div className="flex min-h-svh items-center justify-center bg-muted/40 p-4">
      <div className="w-full max-w-sm space-y-6">
        <div className="flex items-center justify-center gap-2 text-xl font-semibold">
          <LandmarkIcon className="size-6" /> Logis
        </div>
        {current === 'ANONYMOUS' && <PasswordStep onDone={advance} />}
        {current === 'TOTP_REQUIRED' && <TotpStep onDone={advance} onRestart={restart} />}
        {current === 'TOTP_ENROLLMENT_REQUIRED' && <EnrollmentStep onDone={advance} onRestart={restart} />}
        {current === 'SHOW_RECOVERY_CODES' && (
          <RecoveryCodesStep codes={recoveryCodes} onDone={() => setStep(stageAfterCodes)} />
        )}
        {current === 'PASSWORD_CHANGE_REQUIRED' && <PasswordChangeStep onDone={advance} />}
      </div>
    </div>
  )
}

interface StepProps {
  onDone: (response: SessionResponse) => void
}

/** Bei abgelaufener Zwischenstufe zurueck zum Passwort-Schritt. */
function handleStepError(error: unknown, setError: (m: string) => void, onRestart?: () => void) {
  if (error instanceof ApiError && error.message.startsWith('Die Anmeldung ist abgelaufen') && onRestart) {
    onRestart()
    return
  }
  setError(errorMessage(error))
}

function PasswordStep({ onDone }: StepProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onDone(await api<SessionResponse>('/api/auth/login', { json: { username, password } }))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Anmelden</CardTitle>
        <CardDescription>Melde dich mit deinem Benutzernamen und Passwort an.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="username">Benutzername</Label>
            <Input id="username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Passwort</Label>
            <Input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </div>
          <FormError message={error} />
          <Button type="submit" className="w-full" disabled={busy}>
            Weiter
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

function OtpInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <InputOTP maxLength={6} value={value} onChange={onChange} autoFocus aria-label="Code aus der Authenticator-App" inputMode="numeric">
      <InputOTPGroup>
        {Array.from({ length: 6 }, (_, i) => (
          <InputOTPSlot key={i} index={i} />
        ))}
      </InputOTPGroup>
    </InputOTP>
  )
}

function TotpStep({ onDone, onRestart }: StepProps & { onRestart: () => void }) {
  const [code, setCode] = useState('')
  const [useRecovery, setUseRecovery] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onDone(await api<SessionResponse>('/api/auth/totp', { json: { code } }))
    } catch (err) {
      setCode('')
      handleStepError(err, setError, onRestart)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Bestätigungscode</CardTitle>
        <CardDescription>
          {useRecovery
            ? 'Gib einen deiner Wiederherstellungscodes ein. Jeder Code funktioniert nur einmal.'
            : 'Gib den 6-stelligen Code aus deiner Authenticator-App ein.'}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4">
          {useRecovery ? (
            <div className="space-y-2">
              <Label htmlFor="recovery">Wiederherstellungscode</Label>
              <Input id="recovery" autoComplete="one-time-code" placeholder="XXXXX-XXXXX" value={code} onChange={(e) => setCode(e.target.value)} required autoFocus />
            </div>
          ) : (
            <OtpInput value={code} onChange={setCode} />
          )}
          <FormError message={error} />
          <Button type="submit" className="w-full" disabled={busy || (!useRecovery && code.length !== 6)}>
            Anmelden
          </Button>
          <Button
            type="button"
            variant="link"
            className="w-full"
            onClick={() => {
              setUseRecovery(!useRecovery)
              setCode('')
              setError(null)
            }}
          >
            {useRecovery ? 'Code aus der App verwenden' : 'Wiederherstellungscode verwenden'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

interface TotpSetup {
  secret: string
  otpauthUri: string
}

function EnrollmentStep({ onDone, onRestart }: StepProps & { onRestart: () => void }) {
  const [setup, setSetup] = useState<TotpSetup | null>(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<TotpSetup>('/api/auth/totp/setup', { method: 'POST' })
      .then(setSetup)
      .catch((err) => handleStepError(err, setError, onRestart))
  }, [onRestart])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onDone(await api<SessionResponse>('/api/auth/totp/confirm', { json: { code } }))
    } catch (err) {
      setCode('')
      handleStepError(err, setError, onRestart)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Zwei-Faktor-Anmeldung einrichten</CardTitle>
        <CardDescription>
          Scanne den QR-Code mit einer Authenticator-App (z. B. Aegis, 2FAS, Google Authenticator) und bestätige mit dem angezeigten Code.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4">
          {setup && (
            <>
              <div className="flex justify-center rounded-lg border bg-white p-4">
                <QRCodeSVG value={setup.otpauthUri} size={176} aria-label="QR-Code für die Authenticator-App" />
              </div>
              <div className="space-y-1">
                <p className="text-xs text-muted-foreground">Oder Schlüssel manuell eingeben:</p>
                <code className="block break-all rounded bg-muted px-2 py-1 font-mono text-sm" data-testid="totp-secret">
                  {setup.secret}
                </code>
              </div>
            </>
          )}
          <OtpInput value={code} onChange={setCode} />
          <FormError message={error} />
          <Button type="submit" className="w-full" disabled={busy || !setup || code.length !== 6}>
            Bestätigen
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

function RecoveryCodesStep({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Wiederherstellungscodes</CardTitle>
        <CardDescription>
          Bewahre diese Codes sicher auf, z. B. im Passwort-Manager. Mit jedem Code kannst du dich einmal anmelden, falls du keinen
          Zugriff auf deine Authenticator-App hast. Sie werden nur jetzt angezeigt.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <RecoveryCodes codes={codes} />
        <Button className="w-full" onClick={onDone}>
          Ich habe die Codes gesichert
        </Button>
      </CardContent>
    </Card>
  )
}

function PasswordChangeStep({ onDone }: StepProps) {
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (password !== confirm) {
      setError('Die Passwörter stimmen nicht überein.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      onDone(await api<SessionResponse>('/api/auth/password', { json: { newPassword: password } }))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Neues Passwort festlegen</CardTitle>
        <CardDescription>Bitte lege ein eigenes Passwort mit mindestens 12 Zeichen fest.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="new-password">Neues Passwort</Label>
            <Input id="new-password" type="password" autoComplete="new-password" minLength={12} value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus />
          </div>
          <div className="space-y-2">
            <Label htmlFor="confirm-password">Passwort wiederholen</Label>
            <Input id="confirm-password" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
          </div>
          <FormError message={error} />
          <Button type="submit" className="w-full" disabled={busy}>
            Passwort speichern
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
