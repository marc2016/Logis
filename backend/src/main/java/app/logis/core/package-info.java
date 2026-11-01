/**
 * Core of Logis: sign-in, users, module registry, web cross-cutting concerns.
 * The core knows no domain modules.
 */
@ApplicationModule(displayName = "Core", allowedDependencies = "shared")
package app.logis.core;

import org.springframework.modulith.ApplicationModule;
