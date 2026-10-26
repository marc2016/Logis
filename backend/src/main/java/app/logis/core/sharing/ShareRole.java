package app.logis.core.sharing;

/**
 * Rollen an einem Objekt, aufsteigend nach Rechten.
 */
public enum ShareRole {
    VIEWER,
    EDITOR,
    OWNER;

    public boolean atLeast(ShareRole required) {
        return compareTo(required) >= 0;
    }
}
