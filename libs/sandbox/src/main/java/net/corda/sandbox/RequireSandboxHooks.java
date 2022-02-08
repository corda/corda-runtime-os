package net.corda.sandbox;

import org.osgi.annotation.bundle.Requirement;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;
import static net.corda.sandbox.RequireSandboxHooks.SANDBOX_HOOKS;
import static net.corda.sandbox.RequireSandboxHooks.SANDBOX_HOOKS_VERSION;
import static net.corda.sandbox.RequireSandboxHooks.SANDBOX_NAMESPACE;

@Requirement(
    namespace = SANDBOX_NAMESPACE,
    name = SANDBOX_HOOKS,
    version = SANDBOX_HOOKS_VERSION
)
@Target({ PACKAGE, TYPE })
@Retention(CLASS)
public @interface RequireSandboxHooks {
    String SANDBOX_NAMESPACE = "corda.sandbox";
    String SANDBOX_HOOKS = "sandbox.hooks";
    String SANDBOX_HOOKS_VERSION = "1.0.0";
}
