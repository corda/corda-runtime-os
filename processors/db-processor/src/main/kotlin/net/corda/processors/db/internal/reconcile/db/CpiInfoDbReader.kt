package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.cpi.datamodel.findAllCpiMetadata
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.ManifestCorDappInfo
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
import net.corda.processors.db.internal.reconcile.db.CpiInfoDbReader.GetRecordsErrorEvent
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.time.Instant
import java.util.stream.Stream
import javax.persistence.EntityManagerFactory

/**
 * A [ReconcilerReader] for CPI Info database data. This class is a [Lifecycle] and therefore has its own lifecycle.
 * What's special about it is, when its public API [getAllVersionedRecords] method gets called, if an error occurs
 * during the call the exception gets captured and its lifecycle state gets notified with a [GetRecordsErrorEvent].
 * Then depending on if the exception is a transient or not its state should be taken to [LifecycleStatus.DOWN] or
 * [LifecycleStatus.ERROR].
 */
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
    @Suppress("ComplexMethod")
    @VisibleForTesting
    internal fun doGetAllVersionedRecords() =
        entityManagerFactory!!.createEntityManager().run {
            val currentTransaction = transaction
            currentTransaction.begin()
            findAllCpiMetadata().onClose {
                // This class only have access to this em and transaction. This is a read only transaction,
                // only used for making streaming DB data possible.
                currentTransaction.rollback()
                close()
            }.map { cpiMetadataEntity ->
                val cpiMetadata = CpiMetadata(
                    cpiId = CpiIdentifier(
                        cpiMetadataEntity.name,
                        cpiMetadataEntity.version,
                        if (cpiMetadataEntity.signerSummaryHash != "")
                            SecureHash.create(cpiMetadataEntity.signerSummaryHash)
                        else
                            null
                    ),
                    fileChecksum = SecureHash.create(cpiMetadataEntity.fileChecksum),
                    cpksMetadata = mutableListOf<CpkMetadata>().also { cpkMetadataList ->
                        for (cpkMetadataEntity in cpiMetadataEntity.cpks) {
                            cpkMetadataList.add(
                                CpkMetadata(
                                    cpkId = CpkIdentifier(
                                        cpkMetadataEntity.mainBundleName,
                                        cpkMetadataEntity.mainBundleVersion,
                                        if (cpkMetadataEntity.signerSummaryHash != "")
                                            SecureHash.create(cpkMetadataEntity.signerSummaryHash)
                                        else
                                            null
                                    ),
                                    manifest = CpkManifest(
                                        CpkFormatVersion(
                                            cpkMetadataEntity.cpkManifest.cpkFormatVersion.major,
                                            cpkMetadataEntity.cpkManifest.cpkFormatVersion.minor
                                        )
                                    ),
                                    mainBundle = cpkMetadataEntity.cpkMainBundle,
                                    libraries = cpkMetadataEntity.cpkLibraries.toList(),
                                    dependencies = mutableListOf<CpkIdentifier>().also { cpkDependenciesList ->
                                        cpkMetadataEntity.cpkDependencies.forEach { cpkDependencyEntity ->
                                            cpkDependenciesList.add(
                                                CpkIdentifier(
                                                    cpkDependencyEntity.mainBundleName,
                                                    cpkDependencyEntity.mainBundleVersion,
                                                    if (cpkDependencyEntity.signerSummaryHash != "")
                                                        SecureHash.create(cpkDependencyEntity.signerSummaryHash)
                                                    else
                                                        null
                                                )
                                            )
                                        }
                                    },
                                    cordappManifest = CordappManifest(
                                        bundleSymbolicName = cpkMetadataEntity.cpkCordappManifest!!.bundleSymbolicName,
                                        bundleVersion = cpkMetadataEntity.cpkCordappManifest!!.bundleVersion,
                                        minPlatformVersion = cpkMetadataEntity.cpkCordappManifest!!.minPlatformVersion,
                                        targetPlatformVersion = cpkMetadataEntity.cpkCordappManifest!!.targetPlatformVersion,
                                        contractInfo = ManifestCorDappInfo(
                                            cpkMetadataEntity.cpkCordappManifest?.contractInfo?.shortName,
                                            cpkMetadataEntity.cpkCordappManifest?.contractInfo?.vendor,
                                            cpkMetadataEntity.cpkCordappManifest?.contractInfo?.versionId,
                                            cpkMetadataEntity.cpkCordappManifest?.contractInfo?.license
                                        ),
                                        workflowInfo = ManifestCorDappInfo(
                                            cpkMetadataEntity.cpkCordappManifest?.workflowInfo?.shortName,
                                            cpkMetadataEntity.cpkCordappManifest?.workflowInfo?.vendor,
                                            cpkMetadataEntity.cpkCordappManifest?.workflowInfo?.versionId,
                                            cpkMetadataEntity.cpkCordappManifest?.workflowInfo?.license
                                        ),
                                        // TODO below field to be populated from CpkCordappManifestEntity.attributes when added
                                        //  (https://r3-cev.atlassian.net/browse/CORE-4658)
                                        emptyMap()
                                    ),
                                    type = CpkType.parse(cpkMetadataEntity.cpkType ?: ""),
                                    fileChecksum = cpkMetadataEntity.cpkFileChecksum.let { SecureHash.create(it) },
                                    // TODO below field to be populated from CpkMetadataEntity.cordappCertificates when added
                                    //  (https://r3-cev.atlassian.net/browse/CORE-4658)
                                    cordappCertificates = emptySet(),
                                    timestamp = cpkMetadataEntity.insertTimestamp.getOrNow()
                                )
                            )
                        }
                    },
                    groupPolicy = cpiMetadataEntity.groupPolicy,
                    version = cpiMetadataEntity.entityVersion,
                    timestamp = cpiMetadataEntity.insertTimestamp.getOrNow()
                )

                VersionedRecord(
                    version = cpiMetadata.version,
                    isDeleted = cpiMetadataEntity.isDeleted,
                    key = cpiMetadata.cpiId,
                    value = cpiMetadata
                )
            }
        }

    private fun Instant?.getOrNow(): Instant {
        return this ?: Instant.now()
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
        dbConnectionManagerRegistration?.close()
        dbConnectionManagerRegistration = null
        entityManagerFactory = null
    }

    private class GetRecordsErrorEvent(val exception: Exception) : LifecycleEvent
}