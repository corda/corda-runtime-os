package net.corda.dependency.injection.impl

import net.corda.dependency.injection.DependencyInjectionService
import net.corda.dependency.injection.FlowDependencies
import net.corda.v5.application.services.json.JsonMarshallingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference


@Component(service = [FlowDependencies::class])
class FlowDependenciesImpl @Activate constructor(
    @Reference(service = DependencyInjectionService::class)
    private val dependencyInjectionService: DependencyInjectionService,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService
) : FlowDependencies {

    override fun configureInjectionService(dependencyInjector: DependencyInjectionService) {
        dependencyInjectionService.registerSingletonService(JsonMarshallingService::class.java, jsonMarshallingService)
    }
}