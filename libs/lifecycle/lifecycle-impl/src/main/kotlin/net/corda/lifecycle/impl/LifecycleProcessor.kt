package net.corda.lifecycle.impl

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.lifecycle.impl.registry.LifecycleRegistryCoordinatorAccess
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
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
    private val userEventHandler: LifecycleEventHandler
) {

    companion object {
        private val logger = contextLogger()

        internal const val STARTED_REASON = "Component has been started"
        internal const val STOPPED_REASON = "Component has been stopped"
        internal const val ERRORED_REASON = "An unhandled error was encountered by the component"
    }

    /**
     * Process a batch of events.
     *
     * @param coordinator The coordinator scheduling processing of this processor.
     * @param timerGenerator A function to create timers for use if a SetUpTimer event is encountered.
     */
    fun processEvents(
        coordinator: LifecycleCoordinator,
        timerGenerator: (TimerEvent, Long) -> ScheduledFuture<*>
    ): Boolean {
        return state.nextBatch().map { processEvent(it, coordinator, timerGenerator) }.all { it }
    }

    /**
     * Process an individual event.
     */
    private fun processEvent(
        event: LifecycleEvent,
        coordinator: LifecycleCoordinator,
        timerGenerator: (TimerEvent, Long) -> ScheduledFuture<*>
    ): Boolean {
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
                state.registrations[event.registration] = Unit
                event.registration.updateCoordinatorStatus(coordinator, state.status)
                true
            }
            is CancelRegistration -> {
                state.registrations.remove(event.registration)
                true
            }
            is TrackRegistration -> {
                state.trackedRegistrations[event.registration] = Unit
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

    private fun processStartEvent(event: StartEvent, coordinator: LifecycleCoordinator): Boolean {
        return if (!state.isRunning) {
            state.isRunning = true
            state.trackedRegistrations.keys.forEach { it.notifyCurrentStatus() }
            // If there was previously an error, clear this now.
            updateStatus(coordinator, LifecycleStatus.DOWN, STARTED_REASON)
            runUserEventHandler(event, coordinator)
        } else {
            logger.debug { "$name Lifecycle: An attempt was made to start an already running coordinator" }
            true
        }
    }

    private fun processStopEvent(event: StopEvent, coordinator: LifecycleCoordinator): Boolean {
        if (state.isRunning) {
            state.isRunning = false
            val (newStatus, reason) = if (event.errored) {
                Pair(LifecycleStatus.ERROR, ERRORED_REASON)
            } else {
                Pair(LifecycleStatus.DOWN, STOPPED_REASON)
            }
            updateStatus(coordinator, newStatus, reason)
            runUserEventHandler(event, coordinator)
        } else {
            logger.debug { "$name Lifecycle: An attempt was made to stop an already terminated coordinator" }
        }
        return true
    }

    private fun processSetupTimerEvent(
        event: SetUpTimer,
        timerGenerator: (TimerEvent, Long) -> ScheduledFuture<*>
    ): Boolean {
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

    private fun processClose(coordinator: LifecycleCoordinator): Boolean {
        state.isRunning = false
        registry.removeCoordinator(coordinator.name)
        return true
    }

    /**
     * Perform any logic for updating the status of the coordinator. This includes informing other registered
     * coordinators of the status change and informing the registry.
     */
    private fun updateStatus(coordinator: LifecycleCoordinator, newStatus: LifecycleStatus, reason: String) {
        state.status = newStatus
        state.registrations.keys.forEach { it.updateCoordinatorStatus(coordinator, newStatus) }
        registry.updateStatus(coordinator.name, newStatus, reason)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runUserEventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator): Boolean {
        return try {
            userEventHandler.processEvent(event, coordinator)
            true
        } catch (e: Throwable) {
            val errorEvent = ErrorEvent(e)
            logger.info(
                "$name Lifecycle: An error occurred during the processing of event $event by a lifecycle " +
                        "coordinator: ${e.message}. Triggering user event handling.",
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
                            "coordinator: ${e.message}. This coordinator will now shut down.",
                    e
                )
            }
            errorEvent.isHandled
        }
    }
}