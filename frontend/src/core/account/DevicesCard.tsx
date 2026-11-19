import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { MonitorSmartphoneIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { api } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/core/ledger/transactions/format'

/** A remembered device („Angemeldet bleiben“) of the signed-in user. */
export interface RememberedDevice {
  id: number
  browser: string
  os: string
  createdAt: string
  lastUsedAt: string
  current: boolean
}

const devicesKey = ['account', 'devices'] as const

function deviceName(d: RememberedDevice) {
  const browser = d.browser === 'Other' ? 'Unbekannter Browser' : d.browser
  const os = d.os === 'Other' ? 'unbekanntes System' : d.os
  return `${browser} auf ${os}`
}

/** „Angemeldete Geräte“: remembered devices with sign-out per device and for all other devices. */
export function DevicesCard() {
  const queryClient = useQueryClient()
  const { data: devices = [] } = useQuery({
    queryKey: devicesKey,
    queryFn: () => api<RememberedDevice[]>('/api/account/devices'),
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: devicesKey })

  async function signOut(d: RememberedDevice) {
    try {
      await api(`/api/account/devices/${d.id}`, { method: 'DELETE' })
      toast.success(`${deviceName(d)} abgemeldet.`)
    } catch (err) {
      toast.error(errorMessage(err))
    } finally {
      await refresh()
    }
  }

  async function signOutOthers() {
    try {
      await api('/api/account/devices/sign-out-others', { method: 'POST' })
      toast.success('Alle anderen Geräte wurden abgemeldet.')
    } catch (err) {
      toast.error(errorMessage(err))
    } finally {
      await refresh()
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Angemeldete Geräte</CardTitle>
        <CardDescription>
          Geräte, auf denen du bei der Anmeldung „Angemeldet bleiben“ gewählt hast. Sie bleiben bis zu 90 Tage angemeldet.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {devices.length > 0 ? (
          <ul className="divide-y rounded-md border text-sm" aria-label="Angemeldete Geräte">
            {devices.map((d) => (
              <li key={d.id} className="flex flex-wrap items-center gap-2 px-3 py-2">
                <MonitorSmartphoneIcon className="size-4 text-muted-foreground" />
                <span className="min-w-0 flex-1">
                  <span className="font-medium">{deviceName(d)}</span>
                  {d.current && (
                    <Badge variant="secondary" className="ml-2">
                      Dieses Gerät
                    </Badge>
                  )}
                  <span className="block text-xs text-muted-foreground">
                    Angemeldet am {formatDate(d.createdAt.slice(0, 10))}
                    {' · '}
                    Zuletzt verwendet {formatDate(d.lastUsedAt.slice(0, 10))}
                  </span>
                </span>
                <Button variant="outline" size="sm" aria-label={`${deviceName(d)} abmelden`} onClick={() => signOut(d)}>
                  Abmelden
                </Button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-muted-foreground">Kein Gerät ist dauerhaft angemeldet.</p>
        )}
        <Button variant="outline" onClick={signOutOthers}>
          Alle anderen Geräte abmelden
        </Button>
      </CardContent>
    </Card>
  )
}
