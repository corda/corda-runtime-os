package net.corda.lifecycle

/**
 * Creates instances of a [LifecycleCoordinatorScheduler]
 *
 * Different schedulers can be created based on the desired execution
 * semantics of the [LifecycleCoordinator] they are applied to.
 */
interface LifecycleCoordinatorSchedulerFactory {
    fun create(): LifecycleCoordinatorScheduler
}
