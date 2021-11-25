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

        TODO("Add code to load user injectable services.")
        /**
         *  This method should be called from the sandbox initialisation, at that point we
         *  will have access to the bundles containing user defined types that can be injected.
         *  we will need to enumerate these types, create instances of them and then wrap them
         *  in with an instance of InjectableFactory and add them to the injectableFactories list.
         */
    }

    override fun create(): FlowDependencyInjector {
        return FlowDependencyInjectorImpl(sandboxGroupContext!!, injectableFactories)
    }

}