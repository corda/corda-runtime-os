package net.corda.lifecycle.registry.impl

import net.corda.lifecycle.registry.CoordinatorStatus
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Actor to manage updates of the lifecycle status to any monitoring components.
 *
 * This uses a similar model to the lifecycle coordinators. Events can be posted to the status manager to indicate
 * status updates and other internal state changes to make. These are processed on an executor thread, which guarantees
 * that the events are not processed concurrently. Note that the status manager also provides an API to return the
 * current set of statuses, which means the data structure holding these must be concurrent regardless. A copy of the
 * internal state is returned in this case.
 */
class LifecycleStatusManager {

    private companion object {
        private val logger = contextLogger()
    }

    /**
     * Current statuses of coordinators. This must be concurrent as the getStatuses method is called in a different
     * thread to the executor thread.
     */
    private val statuses: MutableMap<String, CoordinatorStatus> = ConcurrentHashMap()

    /**
     * Callbacks. This is guaranteed to be accessed in the same thread and so can be a simple set.
     */
    private val callbacks: MutableSet<CallbackRegistration> = mutableSetOf()

    private val eventQueue = ConcurrentLinkedDeque<RegistryEvent>()

    /**
     * Use a single threaded executor as only a single status manager is likely to exist per-process.
     */
    private val executor = Executors.newSingleThreadExecutor { task ->
        val thread = Thread(task)
        thread.isDaemon = true
        thread
    }

    private val isScheduled = AtomicBoolean(false)

    /**
     * Schedule the process event function if not already scheduled.
     *
     * This ensures that processEvent is always called sequentially.
     */
    private fun scheduleIfRequired() {
        if (!isScheduled.getAndSet(true)) {
            executor.submit(::processEvent)
        }
    }

    /**
     * Process a single registry event.
     *
     * Where this invokes client code, all exceptions must be swallowed to prevent the registry from failing. Unlike the
     * coordinator, no attempt is made to allow the client to handle errors, as clients are expected to be using this
     * purely for monitoring purposes.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun processEvent() {
        val event = eventQueue.poll() ?: return
        when (event) {
            is RegistryEvent.NewCallback -> {
                callbacks.add(event.callback)
            }
            is RegistryEvent.CancelCallback -> {
                callbacks.remove(event.callback)
            }
            is RegistryEvent.UpdateStatus -> {
                val name = event.status.name
                statuses[name] = event.status
                try {
                    // Provide a copy of the current statuses to the client. This guards against a possible future
                    // change where callbacks are not invoked in the same thread as the updating thread as is the case
                    // now.
                    val statusCopy = statuses.toMap()
                    callbacks.forEach { it.invokeCallback(statusCopy, event.status) }
                } catch (e: Exception) {
                    logger.error("An exception occurred in a callback while processing a lifecycle status update." +
                            " Update: ${event.status}, Error: ${e.message}", e)
                }
            }
        }
        isScheduled.set(false)
        scheduleIfRequired()
    }

    /**
     * Post a new event for the status manager to process in its executor thread.
     */
    fun postEvent(event: RegistryEvent) {
        eventQueue.offer(event)
        scheduleIfRequired()
    }

    /**
     * Return a copy of the current status map.
     *
     * Used to service the currentStatuses API. By returning a copy, the data the client sees from the return value of
     * this API will not change underneath them.
     */
    fun getStatuses() : Map<String, CoordinatorStatus> {
        return statuses.toMap()
    }
}