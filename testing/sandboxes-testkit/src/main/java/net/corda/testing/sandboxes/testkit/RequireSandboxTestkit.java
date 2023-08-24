package net.corda.testing.sandboxes.testkit;

import org.osgi.annotation.bundle.Requirement;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

@Requirement(
    namespace = RequireSandboxTestkit.SANDBOX_TESTING_NAMESPACE,
    name = RequireSandboxTestkit.SANDBOX_TESTKIT,
    version = RequireSandboxTestkit.SANDBOX_TESTKIT_VERSION
)
@Target({ PACKAGE, TYPE })
@Retention(CLASS)
public @interface RequireSandboxTestkit {
    String SANDBOX_TESTING_NAMESPACE = "corda.testing.sandbox";
    String SANDBOX_TESTKIT = "sandbox.testkit";
    String SANDBOX_TESTKIT_VERSION = "1.0.0";
}
