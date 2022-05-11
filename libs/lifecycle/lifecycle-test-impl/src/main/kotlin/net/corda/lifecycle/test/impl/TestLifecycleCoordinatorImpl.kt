package net.corda.lifecycle.test.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import org.slf4j.Logger


/**
 * A test version of the manager of lifecycle events for a single component.
 *
 * This class implements the coordinator API, which can be used to model component lifecycle as a queue of events to be
 * handled. The main responsibility of this class is to receive API calls and schedule the processing of lifecycle
 * events.
 *
 * Rather than a production version, in the test manager all events are synchronously processed in a single thread.
 *
 * @param name The name of the component for this lifecycle coordinator.
 * @param userEventHandler The user event handler for lifecycle events.
 */
class TestLifecycleCoordinatorImpl(
    override val name: LifecycleCoordinatorName,
    private val userEventHandler: LifecycleEventHandler,
) : LifecycleCoordinator {

    companion object {
        private val logger: Logger = contextLogger()

        internal const val STARTED_REASON = "Component has been started"
        internal const val STOPPED_REASON = "Component has been stopped"
        internal const val ERRORED_REASON = "An unhandled error was encountered by the component"
    }

    val registrations: MutableSet<TestRegistration> = mutableSetOf()

    private var _status: LifecycleStatus = LifecycleStatus.DOWN

    override val status: LifecycleStatus
        get() = _status

    private var _isClosed = false

    private var _isRunning = false

    /**
     * See [LifecycleCoordinator].
     */
    override fun postEvent(event: LifecycleEvent) {
        if (isClosed) {
            throw LifecycleException("No events can be posted to a closed coordinator. Event: $event")
        }
        processEvent(event)
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent) {
        TODO("Timers not currently supported for testing.")
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun cancelTimer(key: String) {
        TODO("Timers not currently supported for testing.")
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun updateStatus(newStatus: LifecycleStatus, reason: String) {
        _status = newStatus
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun postCustomEventToFollowers(eventPayload: Any) {
        TODO("Custom Events not currently supported for testing.")
    }

    private fun addNewRegistration(coordinators: Set<LifecycleCoordinatorName>): TestRegistration {
        return TestRegistration(coordinators, this).also { registrations.add(it) }
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun followStatusChanges(coordinators: Set<LifecycleCoordinator>): RegistrationHandle {
        if (coordinators.contains(this)) {
            throw LifecycleException("Attempt was made to register coordinator $name on itself")
        }
        return followStatusChangesByName(coordinators.map { it.name }.toSet())
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun followStatusChangesByName(coordinatorNames: Set<LifecycleCoordinatorName>): RegistrationHandle {
        return addNewRegistration(coordinatorNames)
    }

    /**
     * See [LifecycleCoordinator].
     */
    override val isRunning: Boolean
        get() = _isRunning

    /**
     * See [LifecycleCoordinator].
     */
    override val isClosed: Boolean
        get() = _isClosed

    /**
     * Start this coordinator.
     */
    override fun start() {
        processStartEvent()
    }

    /**
     * Stop this coordinator.
     */
    override fun stop() {
        processStopEvent(StopEvent())
    }

    override fun close() {
        stop()
        processClose()
    }

    private fun processEvent(event: LifecycleEvent) {
        return if (isRunning) {
            runUserEventHandler(event)
        } else {
            logger.info("$name Lifecycle: Did not process lifecycle event $event as coordinator is shutdown")
        }
    }

    private fun runUserEventHandler(event: LifecycleEvent) {
        userEventHandler.processEvent(event, this)
    }

    private fun processStartEvent() {
        logger.trace { "Processing start event for $name" }
        return if (!isRunning) {
            _isRunning = true
            // If there was previously an error, clear this now.
            updateStatus(LifecycleStatus.DOWN, STARTED_REASON)
            runUserEventHandler(StartEvent())
        } else {
            logger.info("$name Lifecycle: An attempt was made to start an already running coordinator")
        }
    }

    private fun processStopEvent(event: StopEvent) {
        if (isRunning) {
            _isRunning = false
            val (newStatus, reason) = if (event.errored) {
                Pair(LifecycleStatus.ERROR, ERRORED_REASON)
            } else {
                Pair(LifecycleStatus.DOWN, STOPPED_REASON)
            }
            updateStatus(newStatus, reason)
            runUserEventHandler(event)
        } else {
            logger.info("$name Lifecycle: An attempt was made to stop an already terminated coordinator")
        }
    }

    private fun processClose() {
        _isRunning = false
        _isClosed = true
        registrations.clear()
    }
}
