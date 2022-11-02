package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.utilities.VisibleForTesting
import org.slf4j.LoggerFactory

/**
 * A base class for all DB reconciler reader components which provides standard lifecycle handling.
 */
abstract class DbReconcilerReaderComponent<K : Any, V : Any>(
    name: String,
    coordinatorFactory: LifecycleCoordinatorFactory
) : DbReconcilerReader<K, V> {

    internal val logger = LoggerFactory.getLogger(name)
    final override val lifecycleCoordinatorName = LifecycleCoordinatorName(name)

    internal val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::processEvent)

    private var dbConnectionManagerRegistration: RegistrationHandle? = null

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
        dbConnectionManagerRegistration?.close()
        dbConnectionManagerRegistration = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<DbConnectionManager>()
            )
        )
    }

    private fun onStopEvent() {
        closeResources()
    }

    abstract fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator)

    @Suppress("unused_parameter")
    private fun onGetRecordsErrorEvent(event: GetRecordsErrorEvent, coordinator: LifecycleCoordinator) {
        logger.warn("Processing a ${GetRecordsErrorEvent::class.java.name}")
        // TODO based on exception determine component's next state i.e if transient exception or not -> DOWN or ERROR
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

    internal fun closeResources() {
        dbConnectionManagerRegistration?.close()
        dbConnectionManagerRegistration = null
        close()
    }

    internal abstract fun close()

    internal class GetRecordsErrorEvent(val exception: Exception) : LifecycleEvent
}