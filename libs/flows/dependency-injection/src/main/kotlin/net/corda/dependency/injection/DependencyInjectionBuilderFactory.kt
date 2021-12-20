package net.corda.dependency.injection

/**
 * The DependencyInjectionBuilderFactory is responsible for creating instances of the [DependencyInjectionBuilder]
 */
interface DependencyInjectionBuilderFactory {

    /**
     * Creates a new instance of the [DependencyInjectionBuilder]
     */
    fun create(): DependencyInjectionBuilder
}