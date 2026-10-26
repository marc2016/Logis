package app.logis.core.modules;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
class ModulesController {

    private final List<ModuleDescriptor> modules;

    ModulesController(List<ModuleDescriptor> modules) {
        this.modules = modules.stream().sorted(Comparator.comparingInt(ModuleDescriptor::order)).toList();
    }

    @GetMapping("/api/modules")
    List<ModuleDescriptor> activeModules() {
        return modules;
    }
}
