package app.logis.core.modules;

/**
 * Beschreibt ein aktives Fachmodul. Jedes Modul registriert genau ein Bean dieses Typs,
 * solange es aktiviert ist.
 *
 * @param id    stabile Modul-ID, identisch mit der ID im Frontend
 * @param label Anzeigename
 * @param order Reihenfolge in der Navigation
 */
public record ModuleDescriptor(String id, String label, int order) {
}
