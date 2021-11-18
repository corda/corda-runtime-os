package net.corda.dependency.injection

/**
 * Configures a dependency injector with a set of services available to flows.
 */
interface FlowDependencies {

    /**
     * configures an instance of the [DependencyInjectionService] with the set of services available to flows.
     *
     * @param dependencyInjector The instance of [DependencyInjectionService] to be configured.
     */
    fun configureInjectionService(dependencyInjector: DependencyInjectionService)
}