package net.corda.lifecycle.impl

import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.lifecycle.impl.registry.LifecycleRegistryCoordinatorAccess
import net.corda.lifecycle.registry.LifecycleRegistryException
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

/**
 * Perform processing of lifecycle events.
 *
 * Any modification of the lifecycle coordinator state should occur in this class. This ensures that lifecycle
 * coordinator state is always modified on an executor thread. The coordinator itself ensures that only one attempt to
 * process events is scheduled at once, which in turn prevents race conditions when modifying the state.
 *
 * @param name The name of the component using this processor
 * @param state The state for this lifecycle coordinator
 * @param userEventHandler The event handler the user has registered for use with this coordinator
 */
internal class LifecycleProcessor(
    private val name: LifecycleCoordinatorName,
    private val state: LifecycleStateManager,
    private val registry: LifecycleRegistryCoordinatorAccess,
    private val dependentComponents: DependentComponents?,
    private val userEventHandler: LifecycleEventHandler
) {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        internal const val STARTED_REASON = "Component has been started"
        internal const val STOPPED_REASON = "Component has been stopped"
        internal const val ERRORED_REASON = "An unhandled error was encountered by the component"
    }

    /**
     * A map of the current resources managed by this coordinator.
     */
    private val managedResources = ConcurrentHashMap<String, Resource>()

    /**
     * Process a batch of events.
     *
     * @param coordinator The coordinator scheduling processing of this processor.
     * @param timerGenerator A function to create timers for use if a SetUpTimer event is encountered.
     */
    fun processEvents(
        coordinator: LifecycleCoordinatorInternal,
        timerGenerator: (TimerEvent, Long) -> ScheduledFuture<*>
    ): Boolean {
        return state.nextBatch().map { processEvent(it, coordinator, timerGenerator) }.all { it }
    }

    /**
     * Process an individual event.
     */
    @Suppress("ComplexMethod")
    private fun processEvent(
        event: LifecycleEvent,
        coordinator: LifecycleCoordinatorInternal,
        timerGenerator: (TimerEvent, Long) -> ScheduledFuture<*>
    ): Boolean {
        logger.trace { "LifecycleEvent received for ${coordinator.name}: $event" }
        return when (event) {
            is StartEvent -> {
                processStartEvent(event, coordinator)
            }
            is StopEvent -> {
                processStopEvent(event, coordinator)
            }
            is SetUpTimer -> {
                processSetupTimerEvent(event, timerGenerator)
            }
            is CancelTimer -> {
                state.cancelTimer(event.key)
                true
            }
            is TimerEvent -> {
                if (state.isRunning && state.isTimerRunning(event.key)) {
                    val succeeded = runUserEventHandler(event, coordinator)
                    state.cancelTimer(event.key)
                    succeeded
                } else {
                    logger.trace {
                        "$name Lifecycle: Did not process timer lifecycle event $event with key ${event.key} " +
                                "as coordinator is shutdown"
                    }
                    true
                }
            }
            is NewRegistration -> {
                state.registrations.add(event.registration)
                event.registration.updateCoordinatorStatus(coordinator, state.status)
                true
            }
            is CancelRegistration -> {
                state.registrations.remove(event.registration)
                true
            }
            is TrackRegistration -> {
                state.trackedRegistrations.add(event.registration)
                true
            }
            is StopTrackingRegistration -> {
                state.trackedRegistrations.remove(event.registration)
                true
            }
            is StatusChange -> {
                if (state.isRunning) {
                    updateStatus(coordinator, event.newStatus, event.reason)
                } else {
                    logger.debug {
                        "$name Lifecycle: Did not update coordinator status to ${event.newStatus} as " +
                                "the coordinator is not running"
                    }
                }
                true
            }
            is CloseCoordinator -> {
                processClose(coordinator)
            }
            else -> {
                if (state.isRunning) {
                    runUserEventHandler(event, coordinator)
                } else {
                    logger.debug {
                        "$name Lifecycle: Did not process lifecycle event $event as coordinator is shutdown"
                    }
                    true
                }
            }
        }
    }

    private fun processStartEvent(event: StartEvent, coordinator: LifecycleCoordinatorInternal): Boolean {
        logger.debug { "Processing start event for ${coordinator.name}" }
        return if (!state.isRunning) {
            state.isRunning = true
            state.trackedRegistrations.forEach { it.notifyCurrentStatus() }
            // If there was previously an error, clear this now.
            updateStatus(coordinator, LifecycleStatus.DOWN, STARTED_REASON)
            dependentComponents?.registerAndStartAll(coordinator)
            runUserEventHandler(event, coordinator)
        } else {
            logger.debug { "$name Lifecycle: An attempt was made to start an already running coordinator" }
            true
        }
    }

    private fun processStopEvent(event: StopEvent, coordinator: LifecycleCoordinatorInternal): Boolean {
        logger.debug { "Processing stop event for ${coordinator.name}" }
        if (state.isRunning) {
            state.isRunning = false
            val (newStatus, reason) = if (event.errored) {
                Pair(LifecycleStatus.ERROR, ERRORED_REASON)
            } else {
                Pair(LifecycleStatus.DOWN, STOPPED_REASON)
            }
            try {
                updateStatus(coordinator, newStatus, reason)
            } catch (e: LifecycleRegistryException) {
                // If the coordinator has been closed, then updating the status will fail as it has been removed from
                // the registry.
                logger.debug { "Could not update status as coordinator is closing" }
            }
            if (!event.errored) {
                dependentComponents?.stopAll()
            }
            runUserEventHandler(event, coordinator)
            closeManagedResources(emptySet())
        } else {
            logger.debug { "$name Lifecycle: An attempt was made to stop an already terminated coordinator" }
        }
        return true
    }

    private fun processSetupTimerEvent(
        event: SetUpTimer,
        timerGenerator: (TimerEvent, Long) -> ScheduledFuture<*>
    ): Boolean {
        logger.trace { "Processing setup timer event for ${event.key}" }
        if (state.isRunning) {
            state.setTimer(
                event.key,
                timerGenerator(event.timerEventGenerator(event.key), event.delay)
            )
        } else {
            logger.debug { "$name Lifecycle: Not setting timer with key ${event.key} as coordinator is not running" }
        }
        return true
    }

    private fun processClose(coordinator: LifecycleCoordinatorInternal): Boolean {
        logger.debug { "Closing coordinator ${coordinator.name}" }
        state.isRunning = false
        state.cancelAllTimer()
        state.trackedRegistrations.forEach {
            logger.trace { "Closing $it on ${coordinator.name}." }
            it.close()
        }
        state.trackedRegistrations.clear()
        state.registrations.forEach {
            logger.error("$it on ${coordinator.name} not closed.")
            it.updateCoordinatorStatus(coordinator, LifecycleStatus.ERROR)
        }
        state.registrations.clear()
        closeManagedResources(emptySet())
        managedResources.clear()
        return true
    }

    /**
     * Perform any logic for updating the status of the coordinator. This includes informing other registered
     * coordinators of the status change and informing the registry.
     */
    private fun updateStatus(coordinator: LifecycleCoordinatorInternal, newStatus: LifecycleStatus, reason: String) {
        if (state.status != newStatus) {
            logger.info("Updating coordinator ${coordinator.name} from status ${state.status} to $newStatus. Reason: $reason")
        }
        state.status = newStatus
        state.registrations.forEach { it.updateCoordinatorStatus(coordinator, newStatus) }
        registry.updateStatus(coordinator.name, newStatus, reason)
    }

    private fun runUserEventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator): Boolean {
        return try {
            userEventHandler.processEvent(event, coordinator)
            true
        } catch (e: Throwable) {
            val errorEvent = ErrorEvent(e)
            logger.info(
                "$name Lifecycle: An error occurred during the processing of event $event by a lifecycle " +
                        "coordinator: ${e.message ?: "No exception message provided"}. Triggering user event handling.",
                e
            )
            try {
                userEventHandler.processEvent(errorEvent, coordinator)
            } catch (e: Throwable) {
                errorEvent.isHandled = false
            }
            if (!errorEvent.isHandled) {
                logger.error(
                    "$name Lifecycle: An unhandled error was encountered while processing $event in a lifecycle " +
                            "coordinator: ${e.message ?: "No exception message provided"}. This coordinator will now shut down.",
                    e
                )
            }
            errorEvent.isHandled
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Resource> addManagedResource(name: String, generator: () -> Resource): T {
        return managedResources.compute(name) { _, old ->
            old?.close()
            generator.invoke()
        } as T
    }

    fun getManagedResource(name: String): Resource? {
        return managedResources[name]
    }

    internal fun closeManagedResources(resources: Set<String>?) {
        managedResources.entries.removeIf { (name, resource) ->
            if((resources == null) || (resources.contains(name))) {
                resource.close()
                true
            } else {
                false
            }
        }
    }
}
