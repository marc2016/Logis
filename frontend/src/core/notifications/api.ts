import { api } from '@/lib/api'

/** A way to reach the user, e.g. „E-Mail“; `hint` says what is missing if it is not usable. */
export interface NotificationChannel {
  id: string
  label: string
  usable: boolean
  hint: string | null
}

export interface NotificationSwitch {
  enabled: boolean
  /** Always on: the switch cannot be changed. */
  mandatory: boolean
}

/** A kind of event; labels and groups come from the server, so modules add types without frontend changes. */
export interface NotificationType {
  key: string
  label: string
  group: string
  channels: Record<string, NotificationSwitch>
}

export interface NotificationSettings {
  channels: NotificationChannel[]
  types: NotificationType[]
}

/** The own Pushover key; the key itself is never returned. */
export interface PushoverAccount {
  /** An admin stored an application token. */
  available: boolean
  hasKey: boolean
  /** Pushover rejected the stored key; nothing is sent to it. */
  invalid: boolean
  updatedAt: string | null
}

export const notificationsKey = ['notifications'] as const
export const pushoverAccountKey = ['account', 'pushover'] as const

export const fetchNotifications = () => api<NotificationSettings>('/api/notifications')
export const fetchPushoverAccount = () => api<PushoverAccount>('/api/account/pushover')

export function setNotification(type: string, channel: string, enabled: boolean) {
  return api<void>(`/api/notifications/${encodeURIComponent(type)}/${encodeURIComponent(channel)}`, {
    method: 'PUT',
    json: { enabled },
  })
}
