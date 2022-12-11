package net.corda.sandbox;

import org.osgi.annotation.bundle.Requirement;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;
import static org.osgi.annotation.bundle.Requirement.Cardinality.MULTIPLE;
import static org.osgi.framework.Constants.RESOLUTION_DIRECTIVE;
import static org.osgi.framework.Constants.RESOLUTION_OPTIONAL;

@Requirement(
    namespace = RequireCordaSystem.CORDA_SYSTEM_NAMESPACE,
    name = "*",
    version = RequireCordaSystem.VERSION,
    cardinality = MULTIPLE,
    attribute = { RESOLUTION_DIRECTIVE + ":=" + RESOLUTION_OPTIONAL }
)
@Retention(CLASS)
@Target(TYPE)
public @interface RequireCordaSystem {
    String CORDA_SYSTEM_NAMESPACE = "corda.system";
    String VERSION = "1.0";
}
