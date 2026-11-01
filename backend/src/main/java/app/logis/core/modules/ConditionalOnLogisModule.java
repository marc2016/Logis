package app.logis.core.modules;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers a component only if the module is enabled
 * ({@code logis.modules.<id>.enabled}, default: {@code true}).
 * Every component of a domain module - controllers in particular - carries this annotation.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(LogisModuleCondition.class)
public @interface ConditionalOnLogisModule {

    /** Module ID, e.g. {@code vacationrental}. */
    String value();
}
