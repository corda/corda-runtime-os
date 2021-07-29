package net.corda.lifecycle

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
 * @param state The state for this lifecycle coordinator
 * @param userEventHandler The event handler the user has registered for use with this coordinator
 */
internal class LifecycleProcessor(
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
        coordinator: LifeCycleCoordinator,
        timerGenerator: (TimerEvent, Long) -> ScheduledFuture<*>
    ): Boolean {
        return state.nextBatch().map { processEvent(it, coordinator, timerGenerator) }.all { it }
    }

    private fun processEvent(
        event: LifeCycleEvent,
        coordinator: LifeCycleCoordinator,
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
                        "Did not process timer lifecycle event $event with key ${event.key} as coordinator is shutdown"
                    }
                    true
                }
            }
            else -> {
                if (state.isRunning) {
                    runUserEventHandler(event, coordinator)
                } else {
                    logger.trace { "Did not process lifecycle event $event as coordinator is shutdown" }
                    true
                }
            }
        }
    }

    private fun processStartEvent(event: LifeCycleEvent, coordinator: LifeCycleCoordinator): Boolean {
        return if (!state.isRunning) {
            state.isRunning = true
            runUserEventHandler(event, coordinator)
        } else {
            logger.debug { "An attempt was made to start an already running coordinator" }
            true
        }
    }

    private fun processStopEvent(event: LifeCycleEvent, coordinator: LifeCycleCoordinator): Boolean {
        if (state.isRunning) {
            state.isRunning = false
            runUserEventHandler(event, coordinator)
        } else {
            logger.debug { "An attempt was made to stop an already terminated coordinator" }
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
            logger.debug { "Not setting timer with key ${event.key} as coordinator is not running" }
        }
        return true
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runUserEventHandler(event: LifeCycleEvent, coordinator: LifeCycleCoordinator): Boolean {
        return try {
            userEventHandler.processEvent(event, coordinator)
            true
        } catch (e: Throwable) {
            val errorEvent = ErrorEvent(e)
            logger.info(
                "An error occurred during the processing of event $event by a lifecycle coordinator: ${e.message}." +
                        " Triggering user event handling.", e
            )
            try {
                userEventHandler.processEvent(errorEvent, coordinator)
            } catch (e: Throwable) {
                errorEvent.isHandled = false
            }
            if (!errorEvent.isHandled) {
                logger.error(
                    "An unhandled error was encountered while processing $event in a lifecycle coordinator: " +
                            "${e.message}. This coordinator will now shut down.",
                    e
                )
            }
            errorEvent.isHandled
        }
    }
}