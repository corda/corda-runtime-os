package net.corda.dependency.injection.impl

import net.corda.dependency.injection.DependencyInjectionBuilder
import net.corda.dependency.injection.DependencyInjectionBuilderFactory
import net.corda.dependency.injection.InjectableFactory
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality

class DependencyInjectionBuilderFactoryImpl(
    @Reference(service = InjectableFactory::class, cardinality = ReferenceCardinality.MULTIPLE)
    private val injectableFactories: List<InjectableFactory<*>>
) : DependencyInjectionBuilderFactory {

    override fun create(): DependencyInjectionBuilder {
        return DependencyInjectionBuilderImpl(injectableFactories)
    }
}