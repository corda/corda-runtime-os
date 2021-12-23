package net.corda.dependency.injection.impl

import net.corda.dependency.injection.DependencyInjectionBuilder
import net.corda.dependency.injection.FlowDependencyInjector
import net.corda.dependency.injection.InjectableFactory
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.v5.serialization.SingletonSerializeAsToken

class DependencyInjectionBuilderImpl(
    private val injectableFactories: List<InjectableFactory<*>>,
    private val singletons: List<SingletonSerializeAsToken>
) : DependencyInjectionBuilder {
    private var sandboxGroupContext: MutableSandboxGroupContext? = null

    override fun addSandboxDependencies(sandboxGroupContext: MutableSandboxGroupContext) {
        this.sandboxGroupContext = sandboxGroupContext

        /**
         *  This method should be called from the sandbox initialisation, at that point we
         *  will have access to the bundles containing user defined types that can be injected.
         *  we will need to enumerate these types, build instances of them and then wrap them
         *  in with an instance of InjectableFactory and add them to the injectableFactories list.
         */
    }

    override fun build(): FlowDependencyInjector {
        check(sandboxGroupContext != null) {
            "build can't be called before the sandbox has been set via addSandboxDependencies()"
        }

        return FlowDependencyInjectorImpl(sandboxGroupContext!!.sandboxGroup, injectableFactories, singletons)
    }
}
