package net.corda.processors.db.internal.reconcile.db

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.utilities.VisibleForTesting
import org.slf4j.LoggerFactory

/**
 * A base class for all DB reconciler reader components which provides standard lifecycle handling.
 *
 * Implementations must override the [name], [dependencies], and [coordinatorFactory] properties.
 * Optionally, if custom startup / shutdown logic is required, [onStatusUp] and [onStatusDown] can be implemented with
 * that logic.
 *
 */
abstract class DbReconcilerReaderComponent<K : Any, V : Any>(
    private val coordinatorFactory: LifecycleCoordinatorFactory
) : DbReconcilerReader<K, V> {

    /**
     * Name used for logging and the lifecycle coordinator.
     */
    internal abstract val name: String

    /**
     * Set of dependencies that an implementation on this class must follow lifecycle status of.
     */
    internal abstract val dependencies: Set<LifecycleCoordinatorName>

    internal val logger by lazy { LoggerFactory.getLogger(name) }
    override val lifecycleCoordinatorName by lazy { LifecycleCoordinatorName(name) }

    internal val coordinator by lazy {
        coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::processEvent)
    }

    private var dependencyRegistration: RegistrationHandle? = null

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

    private fun closeResources() {
        dependencyRegistration?.close()
        dependencyRegistration = null
        onStatusDown()
    }

    /**
     * Implementations can override this with custom logic that should run when a component's lifecycle status is UP.
     */
    internal abstract fun onStatusUp()

    /**
     * Implementations can override this with custom logic that should run when a component's lifecycle status is DOWN.
     */
    internal abstract fun onStatusDown()

    internal class GetRecordsErrorEvent(val exception: Exception) : LifecycleEvent
}