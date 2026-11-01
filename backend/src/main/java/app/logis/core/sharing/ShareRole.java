package app.logis.core.sharing;

/**
 * Roles on an object, in ascending order of rights.
 */
public enum ShareRole {
    VIEWER,
    EDITOR,
    OWNER;

    public boolean atLeast(ShareRole required) {
        return compareTo(required) >= 0;
    }
}
