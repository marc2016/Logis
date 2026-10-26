package app.logis.core.modules;

/**
 * Eine Kategorie, die ein Fachmodul mitbringt (z. B. "Reinigung" fuer die Ferienvermietung).
 * Beim Start wird sie angelegt, falls sie fehlt; Admins koennen sie nur archivieren.
 *
 * @param moduleId  ID des Moduls (wie im {@link ModuleDescriptor})
 * @param key       stabiler, global eindeutiger Schluessel, z. B. {@code vacationrental.cleaning}
 * @param income    {@code true} fuer Einnahmen, {@code false} fuer Ausgaben
 * @param name      Anzeigename
 * @param parentKey Schluessel der Hauptkategorie dieses Moduls oder {@code null} fuer eine Hauptkategorie
 */
public record CategoryContribution(String moduleId, String key, boolean income, String name, String parentKey) {
}
