package net.corda.flow.manager.impl.factory

import net.corda.flow.manager.factory.SandboxDependencyInjectionFactory
import net.corda.flow.manager.impl.SandboxDependencyInjectorImpl
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferencePolicy
import org.osgi.service.component.annotations.ReferenceCardinality


@Component(service = [SandboxDependencyInjectionFactory::class])
class SandboxDependencyInjectionFactoryImpl(
    singletons: List<SingletonSerializeAsToken>
) : SandboxDependencyInjectionFactory {

    @Activate
    constructor() : this(mutableListOf())

    @Reference(service = SingletonSerializeAsToken::class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    private val singletons: List<SingletonSerializeAsToken> = singletons

    override fun create(sandboxGroupContext: SandboxGroupContext): SandboxDependencyInjectorImpl {
        /*
        TODOs: the sandbox will be used to load CordApp types used for injection
         */
        return SandboxDependencyInjectorImpl(singletons)
    }
}