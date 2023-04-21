package net.corda.sandboxgroupcontext;

import org.osgi.annotation.bundle.Requirement;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;
import static net.corda.sandbox.RequireSandboxHooks.SANDBOX_NAMESPACE;
import static net.corda.sandboxgroupcontext.RequireSandboxAMQP.SANDBOX_AMQP;
import static net.corda.sandboxgroupcontext.RequireSandboxAMQP.SANDBOX_AMQP_VERSION;

@Requirement(
    namespace = SANDBOX_NAMESPACE,
    name = SANDBOX_AMQP,
    version = SANDBOX_AMQP_VERSION
)
@Target({ PACKAGE, TYPE })
@Retention(CLASS)
public @interface RequireSandboxAMQP {
    String SANDBOX_AMQP = "sandbox.amqp";
    String SANDBOX_AMQP_VERSION = "1.0.0";

    String AMQP_SERIALIZATION_SERVICE = "AMQP_SERIALIZER";
    String AMQP_SERIALIZER_FACTORY = "AMQP_SERIALIZER_FACTORY";
}
