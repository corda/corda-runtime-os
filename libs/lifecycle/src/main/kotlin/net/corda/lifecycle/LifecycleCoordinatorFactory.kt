package net.corda.lifecycle

import net.corda.lifecycle.impl.LifecycleCoordinatorImpl
import java.util.concurrent.atomic.AtomicInteger

/**
 * A factory for building lifecycle coordinator instances.
 *
 * A coordinator is used to manage lifecycle events for a component. An event handler provided by clients of this
 * library uses these events to cope with changes to the current component state.
 */
class LifecycleCoordinatorFactory {

    companion object {
        private val counter = AtomicInteger(0)

        /**
         * Create a new lifecycle coordinator.
         *
         * @param name The name of this coordinator. This is used for diagnostic purposes to identify the component in
         *             logs.
         * @param batchSize The maximum number of lifecycle events to process in a single batch. Larger values may
         *                  improve performance for components that trigger large numbers of lifecycle events.
         * @param handler The event handler for this component that processes lifecycle events. See
         *                [LifecycleEventHandler] for more detail on the event handler.
         */
        fun createCoordinator(name: String, batchSize: Int, handler: LifecycleEventHandler) : LifecycleCoordinator {
            // Add a suffix to account for multiple components registering under the same name (e.g. same component
            // twice).
            val suffix = counter.getAndIncrement()
            val deduplicatedName = "$name-$suffix"
            return LifecycleCoordinatorImpl(deduplicatedName, batchSize, handler)
        }

        /**
         * Create a new lifecycle coordinator.
         *
         * The name of the type provided as a type parameter is used as the coordinator name for diagnostic purposes.
         *
         * @param batchSize The maximum number of lifecycle events to process in a single batch. Larger values may
         *                  improve performance for components that trigger large numbers of lifecycle events.
         * @param handler The event handler for this component that processes lifecycle events. See
         *                [LifecycleEventHandler] for more detail on the event handler.
         */
        inline fun <reified T> createCoordinator(
            batchSize: Int,
            handler: LifecycleEventHandler
        ) : LifecycleCoordinator {
            return createCoordinator(T::class.java.name, batchSize, handler)
        }
    }
}