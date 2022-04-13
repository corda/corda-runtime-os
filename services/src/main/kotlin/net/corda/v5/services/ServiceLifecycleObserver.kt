package net.corda.v5.services

/**
 * Provides lifecycle support to services.
 *
 * Implementing this interface does not guarantee that a service can listen to lifecycle events. Currently the services that can subscribe
 * to lifecycle events are [CordaService]s.
 *
 * @see CordaService
 */
interface ServiceLifecycleObserver {

    /**
     * Method allowing a service to react to certain lifecycle events. Default implementation does nothing so services only need to
     * implement the method if specific behaviour is required.
     *
     * @see ServiceLifecycleEvent
     */
    @JvmDefault
    fun onEvent(event: ServiceLifecycleEvent) {}
}