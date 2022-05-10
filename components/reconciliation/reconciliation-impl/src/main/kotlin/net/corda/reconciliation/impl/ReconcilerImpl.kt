package net.corda.reconciliation.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import kotlin.streams.asSequence

@Suppress("LongParameterList")
class ReconcilerImpl<K : Any, V : Any>(
    private val dbReader: ReconcilerReader<K, V>,
    private val kafkaReader: ReconcilerReader<K, V>,
    private val writer: ReconcilerWriter<V>,
    keyClass: Class<K>,
    valueClass: Class<V>,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private var reconciliationIntervalMs: Long
) : Reconciler {
    companion object {
        private val logger = contextLogger()
    }

    @VisibleForTesting
    internal val name = "${ReconcilerImpl::class.java.name}<${keyClass.name}, ${valueClass.name}>"

    // For now we assume 1 ReconcilerImpl<a_key, a_value> per worker.
    private val coordinator = coordinatorFactory.createCoordinator(LifecycleCoordinatorName(name), ::processEvent)

    private var readersWritersRegistration: RegistrationHandle? = null

    // Errors in sub services will surface here
    @VisibleForTesting
    internal fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ReconcileEvent -> onReconcileEvent(coordinator)
            is UpdateIntervalEvent -> onUpdateIntervalEvent(event)
            is StopEvent -> onStopEvent(coordinator)
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        readersWritersRegistration?.close()
        readersWritersRegistration = coordinator.followStatusChangesByName(
            setOf(
                dbReader.lifecycleCoordinatorName,
                kafkaReader.lifecycleCoordinatorName,
                writer.lifecycleCoordinatorName
            )
        )
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            logger.info("$name starting reconciliations")
            setReconciliationTimerEvent(coordinator)
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            logger.warn(
                "Received a ${RegistrationStatusChangeEvent::class.java.simpleName} with status ${event.status}." +
                        " $name switching to ${event.status}"
            )
            // TODO Revise below actions in case of an error from sub services (DOWN vs ERROR)
            coordinator.updateStatus(event.status)
            coordinator.cancelTimer(name)
            closeResources()
        }
    }

    private fun onReconcileEvent(coordinator: LifecycleCoordinator) {
        logger.debug("Scheduling reconciliation")
        try {
            reconcile()
            setReconciliationTimerEvent(coordinator)
        } catch (e: Exception) {
            // An error here could be a transient or not exception. We should transition to `DOWN` and wait
            // on subsequent `RegistrationStatusChangeEvent` to see if it is going to be a `DOWN` or an `ERROR`.
            logger.warn("Reconciliation failed. Will not schedule any further reconciliations", e)
            coordinator.updateStatus(LifecycleStatus.DOWN)
            closeResources()
        }
    }

    private fun onUpdateIntervalEvent(event: UpdateIntervalEvent) {
        logger.info("Updating interval of $name to ${event.intervalMs} ms")
        reconciliationIntervalMs = event.intervalMs
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        coordinator.cancelTimer(name)
        closeResources()
    }

    // TODO revise check for running state of sub services
    // Perhaps failure at some point during reconciliation could inform us where it stopped so that next reconciliation can take it
    // from there, or perhaps that could be done under timestamps optimization and then we update max timestamp reconciled.
    /**
     * @throws [ReconciliationException] if an error occurs at kafka or db [ReconcilerReader.getAllVersionedRecords].
     */
    @VisibleForTesting
    internal fun reconcile() {
        val kafkaRecords =
            kafkaReader.getAllVersionedRecords()?.asSequence()?.associateBy { it.key } ?: run {
                // Error occurred in kafka getAllVersionedRecords, we need to return and wait on failure to surface
                // by upcoming RegistrationStatusChangeEvent
                throw ReconciliationException("Error occurred while retrieving kafka records")
            }

        val toBeReconciledDbRecords = dbReader.getAllVersionedRecords()?.filter { dbRecord ->
            kafkaRecords[dbRecord.key]?.let { kafkaRecord ->
                dbRecord.version > kafkaRecord.version // reconcile db update
                // || dbRecord.isDeleted == true // reconcile db delete
            } ?: true // reconcile db insert
        } ?: run {
            // Error occurred in db getAllVersionedRecords, we need to return and wait on failure to surface
            // by upcoming RegistrationStatusChangeEvent
            throw ReconciliationException("Error occurred while retrieving db records")
        }

        toBeReconciledDbRecords.use {
            it.forEach { dbRecord ->
                writer.put(dbRecord.value)
            }
        }
    }

    private fun setReconciliationTimerEvent(coordinator: LifecycleCoordinator) {
        logger.debug { "Registering new ${ReconcileEvent::class.simpleName}" }
        coordinator.setTimer(name, reconciliationIntervalMs) { ReconcileEvent(it) }
    }

    override fun updateInterval(intervalMs: Long) {
        coordinator.postEvent(UpdateIntervalEvent(intervalMs))
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("$name starting")
        coordinator.start()
    }

    override fun stop() {
        logger.info("$name stopping")
        coordinator.stop()
        closeResources()
    }

    override fun close() {
        logger.info("$name closing")
        coordinator.close()
        closeResources()
    }

    private fun closeResources() {
        readersWritersRegistration?.close()
        readersWritersRegistration = null
    }

    private data class ReconcileEvent(override val key: String) : TimerEvent

    private data class UpdateIntervalEvent(val intervalMs: Long): LifecycleEvent

    private class ReconciliationException(message: String) : CordaRuntimeException(message)
}