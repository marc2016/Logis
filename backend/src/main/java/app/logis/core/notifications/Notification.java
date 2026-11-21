package app.logis.core.notifications;

/**
 * What a user is told (design D2 of notifications-pushover).
 *
 * @param title     short headline; the subject of a mail
 * @param shortText for channels that leave the system to a third party: MUST NOT contain names or contact data of
 *                  guests or tenants, amounts or booking numbers
 * @param longText  for the user's own mailbox: may contain those details
 * @param path      path of the page in the app the notification is about, e.g. {@code /buchungen/12}; null for none
 */
public record Notification(String title, String shortText, String longText, String path) {

    public Notification {
        if (title == null || title.isBlank() || shortText == null || longText == null) {
            throw new IllegalArgumentException("A notification needs a title and both texts");
        }
        if (path != null && !path.startsWith("/")) {
            throw new IllegalArgumentException("The path must start with a slash");
        }
    }
}
