package net.corda.dependency.injection.impl

import net.corda.dependency.injection.DependencyInjectionBuilder
import net.corda.dependency.injection.DependencyInjectionBuilderFactory
import net.corda.dependency.injection.InjectableFactory
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Component(service = [DependencyInjectionBuilderFactory::class])
class DependencyInjectionBuilderFactoryImpl(
    injectableFactories: List<InjectableFactory<*>>,
    singletons: List<SingletonSerializeAsToken>
) : DependencyInjectionBuilderFactory {

    @Activate
    constructor() : this(mutableListOf(), mutableListOf())

    @Reference(service = InjectableFactory::class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    private val injectableFactories: List<InjectableFactory<*>> = injectableFactories

    @Reference(service = SingletonSerializeAsToken::class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    private val singletons: List<SingletonSerializeAsToken> = singletons

    override fun create(): DependencyInjectionBuilder {
        return DependencyInjectionBuilderImpl(injectableFactories, singletons)
    }
}