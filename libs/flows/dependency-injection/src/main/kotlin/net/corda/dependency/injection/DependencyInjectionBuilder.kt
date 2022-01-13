package net.corda.dependency.injection

import net.corda.sandboxgroupcontext.MutableSandboxGroupContext


/**
 * The DependencyInjectionBuilder is responsible for configuring and creating and instance of a
 * [FlowDependencyInjector] for a given sandbox.
 */
interface DependencyInjectionBuilder {
    /**
     * Registers injectable types available within the sandbox.
     * This call will be made from within the context of the sandbox and therefore allows types defined in CordApps
     * and support [CordaInject] to be registered in the injector.
     *
     * @param sandboxGroupContext The instance of the sandbox to associate the injector with.
     */
    fun addSandboxDependencies(sandboxGroupContext: MutableSandboxGroupContext)

    /**
     * @return the configured instance of the [FlowDependencyInjector]
     */
    fun build(): FlowDependencyInjector

}
