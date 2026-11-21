package app.logis.modules.longtermrental;

import app.logis.core.modules.ConditionalOnLogisModule;
import app.logis.core.modules.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLogisModule(LongTermRentalConfiguration.MODULE_ID)
class LongTermRentalConfiguration {

    static final String MODULE_ID = "longtermrental";

    @Bean
    ModuleDescriptor longTermRentalModule() {
        return new ModuleDescriptor(MODULE_ID, "Dauervermietung", 200);
    }
}
