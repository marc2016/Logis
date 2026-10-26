package app.logis.core.modules;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class LogisModuleCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var attributes = metadata.getAnnotationAttributes(ConditionalOnLogisModule.class.getName());
        if (attributes == null) {
            return true;
        }
        var id = (String) attributes.get("value");
        return context.getEnvironment().getProperty("logis.modules." + id + ".enabled", Boolean.class, true);
    }
}
