package net.corda.processors.db.internal.reconcile.db

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.utilities.VisibleForTesting
import org.slf4j.LoggerFactory

/**
 * A wrapper class for all DB reconciler reader components which provides standard lifecycle handling.
 */
class DbReconcilerReaderWrapper<K : Any, V : Any>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    dbReconcilerReader: DbReconcilerReader<K, V>
) : DbReconcilerReader<K, V> by dbReconcilerReader, Lifecycle {

    private val logger = LoggerFactory.getLogger(name)

    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        ::processEvent
    )

    private var dependencyRegistration: RegistrationHandle? = null
    private var exceptionHandlerHandle: AutoCloseable? = null

    @VisibleForTesting
    internal fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is GetRecordsErrorEvent -> onGetRecordsErrorEvent(event, coordinator)
            is StopEvent -> onStopEvent()
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        dependencyRegistration?.close()
        dependencyRegistration = coordinator.followStatusChangesByName(dependencies)
    }

    private fun onStopEvent() {
        closeResources()
    }

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        if (event.status == LifecycleStatus.UP) {
            onStatusUp()
            exceptionHandlerHandle?.close()
            exceptionHandlerHandle = registerExceptionHandler { e->
                logger.warn("Error while retrieving records for reconciliation", e)
                coordinator.postEvent(GetRecordsErrorEvent(e))
            }
            logger.info("Switching to UP")
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            logger.info(
                "Received a ${RegistrationStatusChangeEvent::class.java.simpleName} with status ${event.status}. " +
                        "Switching to ${event.status}"
            )
            coordinator.updateStatus(event.status)
            closeResources()
        }
    }

    @Suppress("unused_parameter")
    private fun onGetRecordsErrorEvent(event: GetRecordsErrorEvent, coordinator: LifecycleCoordinator) {
        logger.warn("Processing a ${GetRecordsErrorEvent::class.java.name}")
        // TODO CORE-7792 based on exception determine component's next state i.e if transient exception or not -> DOWN or ERROR
//        when (event.exception) {
//        }
        // For now just stopping it with errored false
        coordinator.postEvent(StopEvent())
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("Starting")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping")
        coordinator.stop()
        closeResources()
    }

    private fun closeResources() {
        dependencyRegistration?.close()
        dependencyRegistration = null
        exceptionHandlerHandle?.close()
        exceptionHandlerHandle = null
        onStatusDown()
    }

    internal class GetRecordsErrorEvent(val exception: Exception) : LifecycleEvent
}