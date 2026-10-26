package app.logis.core.sharing;

/**
 * Registriert einen freigebbaren Objekttyp, z. B. {@code account} oder {@code vacationrental.property}.
 * Nur registrierte Typen sind ueber die Freigabe-API erreichbar.
 */
public interface ShareableType {

    /** Stabiler Typname, wie er in {@code core.object_shares.object_type} steht. */
    String type();
}
