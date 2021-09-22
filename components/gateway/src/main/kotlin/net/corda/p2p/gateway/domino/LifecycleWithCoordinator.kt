package net.corda.p2p.gateway.domino

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

abstract class LifecycleWithCoordinator(
    internal val coordinatorFactory: LifecycleCoordinatorFactory,
    instanceId: String?,
) :
    Lifecycle {
    companion object {
        private val logger = contextLogger()
    }

    val name: LifecycleCoordinatorName = LifecycleCoordinatorName(
        javaClass.name,
        instanceId
    )

    private val coordinator = coordinatorFactory.createCoordinator(name, EventHandler())

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    private val waitingForStatusChange = ConcurrentHashMap.newKeySet<CompletableFuture<LifecycleStatus>>()

    // YIFT: The updateStatus won't set the status immediately after the updateStatus?!...
    private val knownStatus = AtomicReference(coordinator.status)
    var status: LifecycleStatus
        get() = knownStatus.get()
        set(newStatus) {
            val oldStatus = knownStatus.getAndSet(newStatus)
            if (oldStatus != newStatus) {
                coordinator.updateStatus(newStatus)
                waitingForStatusChange.forEach {
                    it.complete(newStatus)
                }
            }
            logger.info("Status of $name is $newStatus")
        }

    override fun start() {
        logger.info("Starting $name")
        coordinator.start()
    }
    abstract fun onStart()

    override fun stop() {
        logger.info("Stopping $name")
        coordinator.stop()
    }
    abstract fun onStop()

    fun gotError(error: Throwable) {
        logger.info("Got error in $name")
        coordinator.postEvent(ErrorEvent(error))
    }

    fun followStatusChanges(vararg lifecycles: LifecycleWithCoordinator): RegistrationHandle {
        return followStatusChanges(lifecycles.map { it.name })
    }

    fun followStatusChanges(names: Collection<LifecycleCoordinatorName>): RegistrationHandle {
        return coordinator.followStatusChangesByName(names.toSet())
    }
    open fun onStatusChange(newStatus: LifecycleStatus) {
    }

    fun postEvent(event: LifecycleEvent) {
        coordinator.postEvent(event)
    }
    open fun onCustomEvent(event: LifecycleEvent) {
        logger.warn("Unexpected event $event for $name")
    }

    open fun onError(error: Throwable) {
        status = LifecycleStatus.ERROR
    }

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is ErrorEvent -> {
                    onError(event.cause)
                }
                is StartEvent -> {
                    onStart()
                }
                is StopEvent -> {
                    onStop()
                }
                is RegistrationStatusChangeEvent -> {
                    onStatusChange(event.status)
                }
                else -> {
                    onCustomEvent(event)
                }
            }
        }
    }

    override fun close() {
        val waitForStop = CompletableFuture<LifecycleStatus>()
        waitingForStatusChange.add(waitForStop)

        stop()

        if (status == LifecycleStatus.UP) {
            if (waitForStop.join() != LifecycleStatus.DOWN) {
                logger.warn("Could not stop $name")
            }
        }

        waitingForStatusChange.remove(waitForStop)

        coordinator.close()
    }
}
