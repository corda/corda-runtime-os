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
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import kotlin.streams.asSequence

@Suppress("LongParameterList")
class ReconcilerImpl<K : Any, V : Any>(
    private val dbReader: ReconcilerReader<K, V>,
    private val kafkaReader: ReconcilerReader<K, V>,
    private val writer: ReconcilerWriter<K, V>,
    keyClass: Class<K>,
    valueClass: Class<V>,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private var reconciliationIntervalMs: Long
) : Reconciler {
    @VisibleForTesting
    internal val name = "${ReconcilerImpl::class.java.name}<${keyClass.name}, ${valueClass.name}>"

    private val logger = LoggerFactory.getLogger(name) // Including the generic arguments to differentiate between `ReconcilerImpl` classes

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
            logger.info("Starting reconciliations")
            setReconciliationTimerEvent(coordinator)
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            logger.warn(
                "Received a ${RegistrationStatusChangeEvent::class.java.simpleName} with status ${event.status}." +
                        " Switching to ${event.status}"
            )
            // TODO Revise below actions in case of an error from sub services (DOWN vs ERROR)
            coordinator.updateStatus(event.status)
            coordinator.cancelTimer(name)
            closeResources()
        }
    }

    private fun onReconcileEvent(coordinator: LifecycleCoordinator) {
        logger.info("Initiating reconciliation")
        try {
            val startTime = System.currentTimeMillis()
            reconcile()
            val endTime = System.currentTimeMillis()
            logger.info("Reconciliation completed in ${endTime - startTime} ms")
            setReconciliationTimerEvent(coordinator)
        } catch (e: Exception) {
            // An error here could be a transient or not exception. We should transition to `DOWN` and wait
            // on subsequent `RegistrationStatusChangeEvent` to see if it is going to be a `DOWN` or an `ERROR`.
            logger.warn("Reconciliation failed. Terminating reconciliations", e)
            coordinator.updateStatus(LifecycleStatus.DOWN)
            closeResources()
        }
    }

    private fun onUpdateIntervalEvent(event: UpdateIntervalEvent) {
        logger.info("Updating interval to ${event.intervalMs} ms")
        reconciliationIntervalMs = event.intervalMs
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        coordinator.cancelTimer(name)
        closeResources()
    }

    // TODO Must add to the below DEBUG logging reporting to be reconciled records potentially more
    /**
     * @throws [ReconciliationException] to notify an error occurred at kafka or db [ReconcilerReader.getAllVersionedRecords].
     */
    @Suppress("ComplexMethod")
    @VisibleForTesting
    internal fun reconcile() {
        val kafkaRecords =
            kafkaReader.getAllVersionedRecords()?.asSequence()?.associateBy { it.key }
                ?: throw ReconciliationException("Error occurred while retrieving kafka records")

        val toBeReconciledDbRecords =
            dbReader.getAllVersionedRecords()?.filter { dbRecord ->
                val matchedKafkaRecord = kafkaRecords[dbRecord.key]
                val toBeReconciled = if (matchedKafkaRecord == null) {
                    !dbRecord.isDeleted // reconcile db inserts (i.e. db column cpi.is_deleted == false)
                } else {
                    dbRecord.version > matchedKafkaRecord.version // reconcile db updates
                            || dbRecord.isDeleted // reconcile db deletes
                }
                toBeReconciled
            }
                ?: throw ReconciliationException("Error occurred while retrieving db records")

        toBeReconciledDbRecords.use {
            it.forEach { dbRecord ->
                if (dbRecord.isDeleted) {
                    writer.remove(dbRecord.key)
                } else {
                    writer.put(dbRecord.key, dbRecord.value)
                }
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
        logger.info("Starting")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping")
        coordinator.stop()
        closeResources()
    }

    override fun close() {
        logger.info("Closing")
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