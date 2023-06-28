package net.corda.flow.pipeline.sandbox.factory

import net.corda.flow.pipeline.sandbox.SandboxFlowDependencyInjector
import net.corda.sandboxgroupcontext.SandboxGroupContext

/**
 * The [SandboxFlowDependencyInjectorFactory] is responsible for creating instances of the [SandboxFlowDependencyInjector]
 */
interface SandboxFlowDependencyInjectorFactory {

    /**
     * Creates a new instance of the [SandboxFlowDependencyInjector]
     *
     * @param sandboxGroupContext The instance of the sandbox to associate the injector with.
     */
    fun create(sandboxGroupContext: SandboxGroupContext): SandboxFlowDependencyInjector
}
