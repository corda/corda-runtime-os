package net.corda.dependency.injection.impl

import net.corda.dependency.injection.DependencyInjectionBuilder
import net.corda.dependency.injection.FlowDependencyInjector
import net.corda.dependency.injection.InjectableFactory
import net.corda.virtual.node.sandboxgroup.SandboxGroupContext

class DependencyInjectionBuilderImpl(
    private val injectableFactories: List<InjectableFactory<*>>
) : DependencyInjectionBuilder {
    private var sandboxGroupContext: SandboxGroupContext? = null

    override fun addSandboxDependencies(sandboxGroupContext: SandboxGroupContext) {
        this.sandboxGroupContext = sandboxGroupContext

        /**
         *  This method should be called from the sandbox initialisation, at that point we
         *  will have access to the bundles containing user defined types that can be injected.
         *  we will need to enumerate these types, build instances of them and then wrap them
         *  in with an instance of InjectableFactory and add them to the injectableFactories list.
         */
    }

    override fun build(): FlowDependencyInjector {
        return FlowDependencyInjectorImpl(sandboxGroupContext!!.sandboxGroup, injectableFactories)
    }
}