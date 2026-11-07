package app.logis.modules.vacationrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.modules.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLogisModule(VacationRentalConfiguration.MODULE_ID)
@EnableScheduling
class VacationRentalConfiguration {

    static final String MODULE_ID = "vacationrental";

    @Bean
    ModuleDescriptor vacationRentalModule() {
        return new ModuleDescriptor(MODULE_ID, "Ferienvermietung", 100);
    }
}
