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
import net.corda.lifecycle.registry.LifecycleRegistryException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import org.slf4j.Logger
import java.util.*


/**
 * A manager of lifecycle events for a single component.
 *
 * This class implements the coordinator API, which can be used to model component lifecycle as a queue of events to be
 * handled. The main responsibility of this class is to receive API calls and schedule the processing of lifecycle
 * events.
 *
 * Events are scheduled to be processed on a thread pool. This class ensures that only one call to process is scheduled
 * at once.
 *
 * @param name The name of the component for this lifecycle coordinator.
 * @param batchSize max number of events processed in a single [processEvents] call.
 * @param initialRegistry The registry this coordinator has been registered with. Used to update status for monitoring purposes
 * @param userEventHandler The user event handler for lifecycle events.
 */
class TestLifecycleCoordinatorImpl(
    override val name: LifecycleCoordinatorName,
    private val batchSize: Int,
    initialRegistry: Map<LifecycleCoordinatorName, LifecycleCoordinator>,
    private val userEventHandler: LifecycleEventHandler,
) : LifecycleCoordinator {

    companion object {
        private val logger: Logger = contextLogger()

        internal const val STARTED_REASON = "Component has been started"
        internal const val STOPPED_REASON = "Component has been stopped"
        internal const val ERRORED_REASON = "An unhandled error was encountered by the component"
    }

    private val eventQueue: Queue<LifecycleEvent> = LinkedList()

    private val followedCoordinators: MutableSet<LifecycleCoordinator> = mutableSetOf()

    private var _status: LifecycleStatus = LifecycleStatus.DOWN

    private val registry: MutableMap<LifecycleCoordinatorName, LifecycleCoordinator> = initialRegistry.toMutableMap()

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
        eventQueue.add(event)
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

    /**
     * See [LifecycleCoordinator].
     */
    override fun followStatusChanges(coordinators: Set<LifecycleCoordinator>): RegistrationHandle {
        if (coordinators.contains(this)) {
            throw LifecycleException("Attempt was made to register coordinator $name on itself")
        }
        followedCoordinators.addAll(coordinators)
        return object : RegistrationHandle {
            override fun close() {
            }
        }
    }

    /**
     * See [LifecycleCoordinator].
     */
    override fun followStatusChangesByName(coordinatorNames: Set<LifecycleCoordinatorName>): RegistrationHandle {
        val coordinators = try {
            coordinatorNames.map { registry[it]!! }.toSet()
        } catch (e: LifecycleRegistryException) {
            throw LifecycleException("Failed to register on a coordinator as an invalid name was provided", e)
        }
        return followStatusChanges(coordinators)
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
     *
     * Note that this does not immediately stop, instead a graceful shutdown is attempted where any outstanding events
     * are delivered to the user code.
     */
    override fun stop() {
        processStopEvent(StopEvent())
    }

    override fun close() {
        stop()
        processClose()
        _isClosed = true
    }

    /**
     * Signals the test processor to execute the next [batchSize]
     */
    fun nextBatch(batchSize: Int = 1) {
        val batch = mutableListOf<LifecycleEvent>()
        for (i in 0 until batchSize) {
            val event = eventQueue.poll() ?: break
            batch.add(event)
        }
        processEvents(batch)
    }

    private fun processEvents(events: List<LifecycleEvent>) {
        return events.forEach { event ->
            if (isRunning) {
                runUserEventHandler(event)
            } else {
                logger.info("$name Lifecycle: Did not process lifecycle event $event as coordinator is shutdown")
            }
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
        followedCoordinators.clear()
    }
}
