package net.corda.ledger.verification.sandbox.factory

import net.corda.ledger.verification.sandbox.SandboxVerificationDependencyInjector
import net.corda.sandboxgroupcontext.SandboxGroupContext

/**
 * The [SandboxVerificationDependencyInjectorFactory] is responsible for creating instances of the [SandboxVerificationDependencyInjector]
 */
interface SandboxVerificationDependencyInjectorFactory {

    /**
     * Creates a new instance of the [SandboxVerificationDependencyInjector]
     *
     * @param sandboxGroupContext The instance of the sandbox to associate the injector with.
     */
    fun create(sandboxGroupContext: SandboxGroupContext): SandboxVerificationDependencyInjector
}