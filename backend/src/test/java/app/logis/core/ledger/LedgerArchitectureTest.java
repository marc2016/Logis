package app.logis.core.ledger;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Controller duerfen nicht an den Services (und damit an der Rechtepruefung) vorbei auf Repositories zugreifen.
 */
class LedgerArchitectureTest {

    @Test
    void controllersDoNotUseRepositories() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("app.logis.core.ledger", "app.logis.core.sharing");
        noClasses().that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .because("jeder Datenzugriff muss durch die Freigabepruefung der Services laufen")
                .check(classes);
    }
}
