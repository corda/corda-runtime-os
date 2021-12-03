package net.corda.applications.workers.workercommon

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.processors.processorcommon.Processor

/** Uses the [factory] to create a [LifecycleCoordinator] that starts and stops the [processor]. */
inline fun <reified T> createProcessorCoordinator(factory: LifecycleCoordinatorFactory, processor: Processor) =
    factory.createCoordinator<T> { event, _ ->
        when (event) {
            is StartEvent -> processor.start()
            is StopEvent -> processor.stop()
        }
    }

/** Sets the [coordinator]'s status to [LifecycleStatus.UP]. */
fun statusToUp(coordinator: LifecycleCoordinator): () -> Unit = { coordinator.updateStatus(LifecycleStatus.UP) }

/** Sets the [coordinator]'s status to [LifecycleStatus.DOWN]. */
fun statusToDown(coordinator: LifecycleCoordinator): () -> Unit = { coordinator.updateStatus(LifecycleStatus.DOWN) }

/** Sets the [coordinator]'s status to [LifecycleStatus.ERROR]. */
fun statusToError(coordinator: LifecycleCoordinator): () -> Unit = { coordinator.updateStatus(LifecycleStatus.ERROR) }