import { Fragment } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { errorMessage } from '@/lib/errors'
import { fetchNotifications, notificationsKey, setNotification, type NotificationSettings, type NotificationType } from './api'

/** Groups in the order the server lists their first type. */
function grouped(types: NotificationType[]): [string, NotificationType[]][] {
  const groups = new Map<string, NotificationType[]>()
  for (const type of types) groups.set(type.group, [...(groups.get(type.group) ?? []), type])
  return [...groups.entries()]
}

/** „Benachrichtigungen“: one row per kind of event and one switch per channel, saved at once. */
export function NotificationsCard() {
  const queryClient = useQueryClient()
  const { data } = useQuery({ queryKey: notificationsKey, queryFn: fetchNotifications })

  const change = useMutation({
    mutationFn: (v: { type: string; channel: string; enabled: boolean }) => setNotification(v.type, v.channel, v.enabled),
    onMutate: (v) => {
      // Shown at once; a failed save puts the stored state back.
      queryClient.setQueryData<NotificationSettings>(notificationsKey, (old) =>
        old && {
          ...old,
          types: old.types.map((t) =>
            t.key === v.type ? { ...t, channels: { ...t.channels, [v.channel]: { ...t.channels[v.channel], enabled: v.enabled } } } : t,
          ),
        },
      )
    },
    onError: async (err) => {
      toast.error(errorMessage(err))
      await queryClient.invalidateQueries({ queryKey: notificationsKey })
    },
  })

  if (!data) return null
  const missing = data.channels.filter((c) => !c.usable && c.hint)

  return (
    <Card className="@3xl:col-span-2">
      <CardHeader>
        <CardTitle>Benachrichtigungen</CardTitle>
        <CardDescription>Wähle, worüber Logis dich auf welchem Weg informiert. Änderungen gelten sofort.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b">
                <th scope="col" className="py-2 pr-4 text-left font-medium">
                  Ereignis
                </th>
                {data.channels.map((channel) => (
                  <th key={channel.id} scope="col" className="w-24 px-2 py-2 text-center font-medium">
                    {channel.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {grouped(data.types).map(([group, types]) => (
                <Fragment key={group}>
                  <tr>
                    <th scope="colgroup" colSpan={data.channels.length + 1} className="pt-4 pb-1 text-left text-xs font-medium text-muted-foreground uppercase">
                      {group}
                    </th>
                  </tr>
                  {types.map((type) => (
                    <tr key={type.key} className="border-b last:border-b-0">
                      <th scope="row" className="py-2 pr-4 text-left font-normal">
                        {type.label}
                      </th>
                      {data.channels.map((channel) => {
                        const state = type.channels[channel.id] ?? { enabled: false, mandatory: false }
                        return (
                          <td key={channel.id} className="px-2 py-2 text-center">
                            <Checkbox
                              aria-label={`${type.label}: ${channel.label}`}
                              title={state.mandatory ? 'Diese Benachrichtigung kann nicht abgeschaltet werden.' : undefined}
                              checked={state.enabled}
                              disabled={state.mandatory || !channel.usable}
                              onCheckedChange={(on) => change.mutate({ type: type.key, channel: channel.id, enabled: on === true })}
                            />
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
        {missing.length > 0 && (
          <ul className="space-y-1 text-sm text-muted-foreground">
            {missing.map((channel) => (
              <li key={channel.id}>
                {channel.label}: {channel.hint}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
