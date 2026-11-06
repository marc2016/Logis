package app.logis.core.realestate;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Controllers must not access repositories bypassing the services (and thus the permission checks).
 */
class RealestateArchitectureTest {

    private static final ArchRule CONTROLLERS_WITHOUT_REPOSITORIES = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().areAssignableTo(Repository.class)
            .because("every data access must go through the services' sharing checks");

    @Test
    void controllersDoNotUseRepositories() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("app.logis.core.realestate");
        CONTROLLERS_WITHOUT_REPOSITORIES.check(classes);
    }

    /** A controller that injects a repository, to prove the rule catches it. */
    static class ViolatingController {
        ViolatingController(UnitRepository units) {
        }
    }

    @Test
    void ruleDetectsRepositoryInController() {
        var classes = new ClassFileImporter().importClasses(ViolatingController.class, UnitRepository.class);
        assertThatThrownBy(() -> CONTROLLERS_WITHOUT_REPOSITORIES.check(classes)).isInstanceOf(AssertionError.class);
    }
}
