package app.logis.core.ledger;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Controllers must not access repositories bypassing the services (and thus the permission checks).
 */
class LedgerArchitectureTest {

    @Test
    void controllersDoNotUseRepositories() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("app.logis.core.ledger", "app.logis.core.sharing");
        noClasses().that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .because("every data access must go through the services' sharing checks")
                .check(classes);
    }
}
