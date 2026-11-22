/**
 * Notifications of the core: parts of Logis register event types and say what happened to whom; the core decides
 * per user and channel whether to send, and sends in the background (design D1 of notifications-pushover).
 * Public API: {@link NotificationType}, {@link Notification}, {@link Notifications}; channels implement
 * {@link NotificationChannel}.
 */
@NamedInterface("notifications")
package app.logis.core.notifications;

import org.springframework.modulith.NamedInterface;
