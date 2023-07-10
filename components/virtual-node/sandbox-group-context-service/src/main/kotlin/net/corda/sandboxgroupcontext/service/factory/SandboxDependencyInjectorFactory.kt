package net.corda.sandboxgroupcontext.service.factory

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.service.SandboxDependencyInjector

/**
 * The [SandboxDependencyInjectorFactory] is responsible for creating instances of the [SandboxDependencyInjector]
 */
interface SandboxDependencyInjectorFactory {

    /**
     * Creates a new instance of the [SandboxDependencyInjector]
     *
     * @param sandboxGroupContext The instance of the sandbox to associate the injector with.
     */
    fun <T: Any> create(sandboxGroupContext: SandboxGroupContext): SandboxDependencyInjector<T>
}