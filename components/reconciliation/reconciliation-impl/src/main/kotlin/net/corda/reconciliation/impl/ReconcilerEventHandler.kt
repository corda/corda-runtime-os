package net.corda.reconciliation.impl

import kotlin.streams.asSequence
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.utilities.debug
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class ReconcilerEventHandler<K : Any, V : Any>(
    private val dbReader: ReconcilerReader<K, V>,
    private val kafkaReader: ReconcilerReader<K, V>,
    private val writer: ReconcilerWriter<K, V>,
    keyClass: Class<K>,
    valueClass: Class<V>,
    var reconciliationIntervalMs: Long,
) : LifecycleEventHandler {

    val name = "${ReconcilerEventHandler::class.java.name}<${keyClass.name}, ${valueClass.name}>"

    // Including the generic arguments to logger to differentiate between `ReconcilerEventHandler` parameterized classes
    private val logger = LoggerFactory.getLogger(name)

    private val timerKey = name

    private var readersWritersRegistration: RegistrationHandle? = null

    // Errors in sub services will surface here
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is ReconcileEvent -> reconcileAndScheduleNext(coordinator)
            is UpdateIntervalEvent -> onUpdateIntervalEvent(event, coordinator)
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
            coordinator.updateStatus(LifecycleStatus.UP)
            reconcileAndScheduleNext(coordinator)
        } else {
            // TODO Revise below actions in case of an error from sub services (DOWN vs ERROR)
            coordinator.updateStatus(event.status)
            coordinator.cancelTimer(timerKey)
        }
    }

    private fun reconcileAndScheduleNext(coordinator: LifecycleCoordinator) {
        logger.info("Initiating reconciliation")
        try {
            val startTime = System.currentTimeMillis()
            reconcile()
            val endTime = System.currentTimeMillis()
            logger.info("Reconciliation completed in ${endTime - startTime} ms")
            scheduleNextReconciliation(coordinator)
        } catch (e: Exception) {
            // An error here could be a transient or not exception. We should transition to `DOWN` and wait
            // on subsequent `RegistrationStatusChangeEvent` to see if it is going to be a `DOWN` or an `ERROR`.
            logger.warn("Reconciliation failed. Terminating reconciliations", e)
            coordinator.updateStatus(LifecycleStatus.DOWN)
        }
    }

    // TODO following method should be extracted to dedicated file, to be tested separately
    // TODO Must add to the below DEBUG logging reporting to be reconciled records potentially more
    /**
     * @throws [ReconciliationException] to notify an error occurred at kafka or db [ReconcilerReader.getAllVersionedRecords].
     */
    @Suppress("ComplexMethod")
    fun reconcile() {
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

                if (toBeReconciled) {
                    logger.debug { "DbRecord[k=${dbRecord.key},v=${dbRecord.version}] marked for reconciliation" }
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

    private fun scheduleNextReconciliation(coordinator: LifecycleCoordinator) {
        logger.debug { "Registering new ${ReconcileEvent::class.simpleName}" }
        coordinator.setTimer(timerKey, reconciliationIntervalMs) { ReconcileEvent(it) }
    }

    private fun onUpdateIntervalEvent(event: UpdateIntervalEvent, coordinator: LifecycleCoordinator) {
        logger.info("Updating interval to ${event.intervalMs} ms")
        val newIntervalMs = event.intervalMs
        reconciliationIntervalMs = newIntervalMs
        coordinator.setTimer(timerKey, newIntervalMs) { ReconcileEvent(it) }
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        coordinator.cancelTimer(timerKey)
        closeResources()
    }

    private fun closeResources() {
        readersWritersRegistration?.close()
        readersWritersRegistration = null
    }

    internal data class ReconcileEvent(override val key: String) : TimerEvent

    internal data class UpdateIntervalEvent(val intervalMs: Long): LifecycleEvent

    private class ReconciliationException(message: String) : CordaRuntimeException(message)
}