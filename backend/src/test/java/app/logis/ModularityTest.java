package app.logis;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(LogisApplication.class);

    @Test
    void detectsExpectedModules() {
        assertThat(modules.stream().map(m -> m.getIdentifier().toString()))
                .containsExactlyInAnyOrder("core", "shared", "vacationrental");
    }

    @Test
    void coreExposesNamedInterfacesForModules() {
        var core = modules.getModuleByName("core").orElseThrow();
        assertThat(core.getNamedInterfaces().stream().map(i -> i.getName()))
                .contains("modules", "sharing", "realestate");
    }

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }
}
