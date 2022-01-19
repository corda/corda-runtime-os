package net.corda.flow.manager.factory

import net.corda.flow.manager.SandboxDependencyInjector
import net.corda.sandboxgroupcontext.SandboxGroupContext

/**
 * The DependencyInjectionBuilderFactory is responsible for creating instances of the [SandboxDependencyInjectionBuilder]
 */
interface SandboxDependencyInjectionFactory {

    /**
     * Creates a new instance of the [SandboxDependencyInjector]
     *
     * @param sandboxGroupContext The instance of the sandbox to associate the injector with.
     */
    fun create(sandboxGroupContext: SandboxGroupContext): SandboxDependencyInjector
}