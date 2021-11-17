package net.corda.dependency.injection

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaInjectPreStart
import net.corda.v5.application.services.CordaService
import net.corda.v5.base.annotations.CordaInternal
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 * An interface for handling injection of dependencies.
 */
interface DependencyInjectionService {
    /**
     * Register an injector for an interface. The injector is responsible for creating a new instance of the implementation
     * to be injector for the interface.
     *
     * @param injectableInterface The interface for which the implementation injector will create an implementation for.
     * @param implementationInjector The injector responsible for creating an instance of the implementation to be injected.
     * @param <U> The type of the implementation being registered. Must extend the interface it's being injected for.
     * @param <T> The type of the interface being registered for injection.
     *
     * @return true if service was registered, false if not.
     */
    fun <U : T, T : Any> registerDynamicService(
        injectableInterface: Class<T>,
        implementationInjector: DependencyInjector<U>
    ): Boolean

    /**
     * Register an implementation for an interface. The same singleton instance of the implementation will always be injected
     * for the interface.
     *
     * @param injectableInterface The interface for which the implementation will be injected for.
     * @param implementation The implementation which will be injected the the interface.
     * @param <U> The type of the implementation being registered. Must extend the interface it's being injected for.
     * @param <T> The type of the interface being registered for injection.
     *
     * @return true if service was registered, false if not.
     */
    fun <U : T, T : Any> registerSingletonService(
        injectableInterface: Class<T>,
        implementation: U
    ): Boolean

    /**
     * Inject all available dependencies which are required for service start up into a given corda service.
     * These dependencies are annotated with [CordaInjectPreStart]
     */
    fun injectPreStartDependencies(cordaService: CordaService)

    /**
     * Inject all available dependencies into a given service which implements [SingletonSerializeAsToken]. This could
     * be corda services or notary services for example.
     */
    fun injectDependencies(service: SingletonSerializeAsToken)

    /**
     * Inject all available dependencies into a given flow.
     */
    fun injectDependencies(flow: Flow<*>, flowStateMachineInjectable: FlowStateMachineInjectable)

    fun getRegisteredAsTokenSingletons(): Set<SingletonSerializeAsToken>
}

/**
 * Interface which is used to create dynamic injectables for the dependency injection logic.
 * It is responsible for creating a new instance of a class to be injected each time it is called.
 *
 * @param <T> The type of the class to be initialised before injection
 */
@CordaInternal
fun interface DependencyInjector<T> {
    /**
     * Initialises new instance of class of type T for dependency injection.
     *
     * @param flowStateMachineInjectable It can only be a state machine object, which can be used during initialisation.
     * @param currentFlow A flow object which can be used during initialisation.
     * @return A new instance of type T
     */
    fun inject(
        flowStateMachineInjectable: FlowStateMachineInjectable?,
        currentFlow: Flow<*>?,
        currentService: SingletonSerializeAsToken?
    ): T
}
