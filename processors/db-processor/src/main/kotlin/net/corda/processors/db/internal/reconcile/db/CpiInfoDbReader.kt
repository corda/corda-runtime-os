package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.cpi.datamodel.findAllCpiMetadata
import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.util.stream.Stream
import javax.persistence.EntityManagerFactory
import kotlin.streams.asSequence

// Maybe this class needs to be moved elsewhere, although it should only be used by `DBProcessorImpl`.
class CpiInfoDbReader(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager
) : ReconcilerReader<CpiIdentifier, CpiMetadata>, Lifecycle {
    companion object {
        val logger = contextLogger()
    }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<CpiInfoDbReader>()

    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::processEvent)

    private var dbConnectionManagerRegistration: RegistrationHandle? = null

    @VisibleForTesting
    internal var entityManagerFactory: EntityManagerFactory? = null

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

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            entityManagerFactory = dbConnectionManager.getClusterEntityManagerFactory()
            coordinator.updateStatus(LifecycleStatus.UP)
        } else {
            logger.warn("Received a ${RegistrationStatusChangeEvent::class.java.simpleName} with status ${event.status}.")
            coordinator.updateStatus(event.status)
            closeResources()
        }
    }

    @Suppress("warnings")
    private fun onGetRecordsErrorEvent(event: GetRecordsErrorEvent, coordinator: LifecycleCoordinator) {
        logger.warn("Processing a ${GetRecordsErrorEvent::class.java.name}")
//        when (event.exception) {
//            // TODO based on exception determine component's next state i.e if transient exception or not -> DOWN or ERROR
//        }
        // TODO for now just stopping it with errored false
        coordinator.postEvent(StopEvent())
    }

    private fun onStopEvent() {
        closeResources()
    }

    /**
     * [getAllVersionedRecords] is public API for this service i.e. it can be called by other lifecycle services,
     * therefore it must be guarded from thrown exceptions. No exceptions should escape from it, instead an
     * event should be scheduled notifying the service about the error. Then the calling service which should
     * be following this service will get notified of this service's stop event as well.
     */
    override fun getAllVersionedRecords(): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>>? {
        return try {
            doGetAllVersionedRecords()
        } catch (e: Exception) {
            logger.warn("Error while retrieving records for reconciliation", e)
            coordinator.postEvent(GetRecordsErrorEvent(e))
            null
        }
    }

    // Separating actual logic from lifecycle stuff so it can be unit tested.
    @VisibleForTesting
    internal fun doGetAllVersionedRecords() =
        entityManagerFactory!!.createEntityManager().run {
            findAllCpiMetadata().onClose {
                // closing em after the stream gets used outside the scope of this method
                close()
            }.map { entity ->
                val cpiMetadata = CpiMetadata(
                    CpiIdentifier(
                        entity.name,
                        entity.version,
                        SecureHash.create(entity.signerSummaryHash)
                    ),
                    SecureHash.create(entity.fileChecksum),
                    // The below empty list needs to be populated once we store [CpkMetadata] properties in database
                    // (https://r3-cev.atlassian.net/browse/CORE-4658), it will now wipe out values on Kafka.
                    listOf(),
                    entity.groupPolicy,
                    entity.entityVersion
                )

                VersionedRecord(
                    version = cpiMetadata.version,
                    isDeleted = entity.isDeleted,
                    key = cpiMetadata.cpiId,
                    value = cpiMetadata
                )
            }
        }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("${CpiInfoDbReader::class.java.name} starting")
        coordinator.start()
    }

    override fun stop() {
        logger.info("${CpiInfoDbReader::class.java.name} stopping")
        coordinator.stop()
        closeResources()
    }

    private fun closeResources() {
        dbConnectionManagerRegistration?.close()
        dbConnectionManagerRegistration = null
        entityManagerFactory = null
    }

    private class GetRecordsErrorEvent(val exception: Exception) : LifecycleEvent
}