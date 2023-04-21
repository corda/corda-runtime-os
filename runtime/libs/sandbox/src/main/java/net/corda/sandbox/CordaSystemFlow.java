package net.corda.sandbox;

import org.osgi.annotation.bundle.Capability;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;
import static net.corda.sandbox.RequireCordaSystem.CORDA_SYSTEM_NAMESPACE;
import static net.corda.sandbox.RequireCordaSystem.VERSION;

@Capability(
    namespace = CORDA_SYSTEM_NAMESPACE,
    name = "flow",
    version = VERSION
)
@Retention(CLASS)
@Target(TYPE)
public @interface CordaSystemFlow {
}
