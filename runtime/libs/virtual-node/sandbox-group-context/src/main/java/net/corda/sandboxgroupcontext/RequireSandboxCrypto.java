package net.corda.sandboxgroupcontext;

import org.osgi.annotation.bundle.Requirement;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;
import static net.corda.sandbox.RequireSandboxHooks.SANDBOX_NAMESPACE;
import static net.corda.sandboxgroupcontext.RequireSandboxCrypto.SANDBOX_CRYPTO;
import static net.corda.sandboxgroupcontext.RequireSandboxCrypto.SANDBOX_CRYPTO_VERSION;

@Requirement(
    namespace = SANDBOX_NAMESPACE,
    name = SANDBOX_CRYPTO,
    version = SANDBOX_CRYPTO_VERSION
)
@Target({ PACKAGE, TYPE })
@Retention(CLASS)
public @interface RequireSandboxCrypto {
    String SANDBOX_CRYPTO = "sandbox.crypto";
    String SANDBOX_CRYPTO_VERSION = "1.0.0";
}
