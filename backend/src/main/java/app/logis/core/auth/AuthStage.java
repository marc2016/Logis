package app.logis.core.auth;

/**
 * Zustand des mehrstufigen Logins. Nur {@link #AUTHENTICATED} gibt Zugriff auf fachliche Endpunkte.
 */
public enum AuthStage {
    ANONYMOUS,
    TOTP_ENROLLMENT_REQUIRED,
    TOTP_REQUIRED,
    PASSWORD_CHANGE_REQUIRED,
    AUTHENTICATED
}
