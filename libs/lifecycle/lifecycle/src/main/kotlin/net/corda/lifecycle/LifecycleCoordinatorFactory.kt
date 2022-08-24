package net.corda.lifecycle

/**
 * A factory for building lifecycle coordinator instances.
 *
 * A coordinator is used to manage lifecycle events for a component. An event handler provided by clients of this
 * library uses these events to cope with changes to the current component state.
 */
interface LifecycleCoordinatorFactory {

    companion object {
        private const val DEFAULT_BATCH_SIZE = 10
    }

    /**
     * Create a new lifecycle coordinator.
     *
     * @param name The name of this coordinator. This is used for diagnostic purposes to identify the component in
     *             logs.
     * @param batchSize The maximum number of lifecycle events to process in a single batch. Larger values may
     *                  improve performance for components that trigger large numbers of lifecycle events.
     * @param dependentComponents A set of static singleton component dependencies this coordinator will track.
     *                            These dependencies will be stopped/started alongside this component.  Note that
     *                            the component for this coordinator should also be a static singleton component.
     * @param handler The event handler for this component that processes lifecycle events. See
     *                [LifecycleEventHandler] for more detail on the event handler.
     * @return a new [LifecycleCoordinator] to manage the lifecycle of the component.
     */
    fun createCoordinator(
        name: LifecycleCoordinatorName,
        batchSize: Int,
        dependentComponents: DependentComponents?,
        handler: LifecycleEventHandler
    ): LifecycleCoordinator

    /**
     * Create a new lifecycle coordinator with the default batch size.
     *
     * @param name The name of this coordinator.
     * @param handler The event handler for the component that processes lifecycle events. See [LifecycleEventHandler]
     * @return a new [LifecycleCoordinator] to manage the lifecycle of the component.
     */
    fun createCoordinator(name: LifecycleCoordinatorName, handler: LifecycleEventHandler): LifecycleCoordinator {
        return createCoordinator(name, DEFAULT_BATCH_SIZE, null, handler)
    }

    /**
     * Create a new lifecycle coordinator with the default batch size.
     *
     * @param name The name of this coordinator.
     * @param dependentComponents A set of static singleton component dependencies this coordinator will track.
     *                            These dependencies will be stopped/started alongside this component.  Note that
     *                            the component for this coordinator should also be a static singleton component.
     * @param handler The event handler for the component that processes lifecycle events. See [LifecycleEventHandler]
     * @return a new [LifecycleCoordinator] to manage the lifecycle of the component.
     */
    fun createCoordinator(
        name: LifecycleCoordinatorName,
        dependentComponents: DependentComponents,
        handler: LifecycleEventHandler
    ): LifecycleCoordinator {
        return createCoordinator(name, DEFAULT_BATCH_SIZE, dependentComponents, handler)
    }
}


/**
 * Create a new lifecycle coordinator.
 *
 * The name of the type provided as a type parameter is used as the coordinator name for diagnostic purposes.
 *
 * Note that this utility can only be used if the component can only be instantiated once. If the component is expected
 * to be used multiple times, then a [LifecycleCoordinatorName] should be created with an instance ID set.
 *
 * @param handler The event handler for this component that processes lifecycle events. See
 *                [LifecycleEventHandler] for more detail on the event handler.
 * @return a new [LifecycleCoordinator] to manage the lifecycle of the component.
 */
inline fun <reified T> LifecycleCoordinatorFactory.createCoordinator(
    handler: LifecycleEventHandler
): LifecycleCoordinator {
    return this.createCoordinator(LifecycleCoordinatorName.forComponent<T>(), handler)
}


/**
 * Create a new lifecycle coordinator.
 *
 * The name of the type provided as a type parameter is used as the coordinator name for diagnostic purposes.
 *
 * Note that this utility can only be used if the component can only be instantiated once. If the component is expected
 * to be used multiple times, then a [LifecycleCoordinatorName] should be created with an instance ID set.
 *
 * @param dependentComponents A set of static singleton component dependencies this coordinator will track.
 *                            These dependencies will be stopped/started alongside this component.  Note that
 *                            the component for this coordinator should also be a static singleton component.
 * @param handler The event handler for this component that processes lifecycle events. See
 *                [LifecycleEventHandler] for more detail on the event handler.
 * @return a new [LifecycleCoordinator] to manage the lifecycle of the component.
 */
inline fun <reified T> LifecycleCoordinatorFactory.createCoordinator(
    dependentComponents: DependentComponents,
    handler: LifecycleEventHandler
): LifecycleCoordinator {
    return this.createCoordinator(LifecycleCoordinatorName.forComponent<T>(), dependentComponents, handler)
}

