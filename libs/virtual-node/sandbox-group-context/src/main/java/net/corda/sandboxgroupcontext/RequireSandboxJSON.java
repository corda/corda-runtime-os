package net.corda.sandboxgroupcontext;

import org.osgi.annotation.bundle.Requirement;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;
import static net.corda.sandbox.RequireSandboxHooks.SANDBOX_NAMESPACE;
import static net.corda.sandboxgroupcontext.RequireSandboxJSON.SANDBOX_JSON;
import static net.corda.sandboxgroupcontext.RequireSandboxJSON.SANDBOX_JSON_VERSION;

@Requirement(
    namespace = SANDBOX_NAMESPACE,
    name = SANDBOX_JSON,
    version = SANDBOX_JSON_VERSION
)
@Target({ PACKAGE, TYPE })
@Retention(CLASS)
public @interface RequireSandboxJSON {
    String SANDBOX_JSON = "sandbox.json";
    String SANDBOX_JSON_VERSION = "1.0.0";
}
