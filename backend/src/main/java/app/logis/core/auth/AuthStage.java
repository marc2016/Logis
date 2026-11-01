package app.logis.core.auth;

/**
 * State of the multi-step sign-in. Only {@link #AUTHENTICATED} grants access to domain endpoints.
 */
public enum AuthStage {
    ANONYMOUS,
    TOTP_ENROLLMENT_REQUIRED,
    TOTP_REQUIRED,
    PASSWORD_CHANGE_REQUIRED,
    AUTHENTICATED
}
