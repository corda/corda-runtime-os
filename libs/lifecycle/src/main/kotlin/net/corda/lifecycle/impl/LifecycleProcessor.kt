package net.corda.lifecycle.impl

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
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
    private val name: String,
    private val state: LifecycleStateManager,
    private val userEventHandler: LifecycleEventHandler
) {

    companion object {
        private val logger = contextLogger()
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
                    state.status = event.newStatus
                    state.registrations.forEach { it.updateCoordinatorStatus(coordinator, event.newStatus) }
                } else {
                    logger.debug {
                        "$name Lifecycle: Did not update coordinator status to ${event.newStatus} as " +
                                "the coordinator is not running"
                    }
                }
                true
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
            state.trackedRegistrations.forEach { it.notifyCurrentStatus() }
            // If there was previously an error, clear this now.
            state.status = LifecycleStatus.DOWN
            state.registrations.forEach { it.updateCoordinatorStatus(coordinator, LifecycleStatus.DOWN) }
            runUserEventHandler(event, coordinator)
        } else {
            logger.debug { "$name Lifecycle: An attempt was made to start an already running coordinator" }
            true
        }
    }

    private fun processStopEvent(event: StopEvent, coordinator: LifecycleCoordinator): Boolean {
        if (state.isRunning) {
            state.isRunning = false
            state.status = if (event.errored) {
                LifecycleStatus.ERROR
            } else {
                LifecycleStatus.DOWN
            }
            state.registrations.forEach { it.updateCoordinatorStatus(coordinator, state.status) }
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