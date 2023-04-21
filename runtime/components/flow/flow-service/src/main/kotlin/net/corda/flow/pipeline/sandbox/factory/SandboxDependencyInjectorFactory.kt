package net.corda.flow.pipeline.sandbox.factory

import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.sandboxgroupcontext.SandboxGroupContext

/**
 * The [SandboxDependencyInjectorFactory] is responsible for creating instances of the [SandboxDependencyInjector]
 */
interface SandboxDependencyInjectorFactory {

    /**
     * Creates a new instance of the [SandboxDependencyInjector]
     *
     * @param sandboxGroupContext The instance of the sandbox to associate the injector with.
     */
    fun create(sandboxGroupContext: SandboxGroupContext): SandboxDependencyInjector
}
