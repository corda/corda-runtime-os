@Capability(
    namespace = SANDBOX_NAMESPACE,
    name = SANDBOX_HOOKS,
    version = SANDBOX_HOOKS_VERSION
)
@QuasarIgnoreSubPackages
package net.corda.sandboxhooks;

import co.paralleluniverse.quasar.annotations.QuasarIgnoreSubPackages;
import org.osgi.annotation.bundle.Capability;

import static net.corda.sandbox.RequireSandboxHooks.SANDBOX_HOOKS;
import static net.corda.sandbox.RequireSandboxHooks.SANDBOX_HOOKS_VERSION;
import static net.corda.sandbox.RequireSandboxHooks.SANDBOX_NAMESPACE;
