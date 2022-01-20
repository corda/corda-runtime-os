package net.corda.flow.manager.impl.factory

import net.corda.flow.manager.SandboxDependencyInjector
import net.corda.flow.manager.factory.SandboxDependencyInjectionFactory
import net.corda.flow.manager.impl.SandboxDependencyInjectorImpl
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC
import org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE

@Suppress("CanBePrimaryConstructorProperty")
@Component(service = [SandboxDependencyInjectionFactory::class])
class SandboxDependencyInjectionFactoryImpl(
    singletons: List<SingletonSerializeAsToken>
) : SandboxDependencyInjectionFactory {

    @Suppress("unused")
    @Activate
    constructor() : this(mutableListOf())

    // We cannot use constructor injection with DYNAMIC policy.
    @Reference(service = SingletonSerializeAsToken::class, cardinality = MULTIPLE, policy = DYNAMIC)
    private val singletons: List<SingletonSerializeAsToken> = singletons

    override fun create(sandboxGroupContext: SandboxGroupContext): SandboxDependencyInjector {
        /*
        TODOs: the sandbox will be used to load CordApp types used for injection
         */
        return SandboxDependencyInjectorImpl(singletons)
    }
}