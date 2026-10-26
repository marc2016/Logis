package app.logis.core.modules;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registriert eine Komponente nur, wenn das Modul aktiviert ist
 * ({@code logis.modules.<id>.enabled}, Standard: {@code true}).
 * Jede Komponente eines Fachmoduls - insbesondere Controller - traegt diese Annotation.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(LogisModuleCondition.class)
public @interface ConditionalOnLogisModule {

    /** Modul-ID, z. B. {@code vacationrental}. */
    String value();
}
