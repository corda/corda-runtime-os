package net.corda.processors.db.internal.reconcile.db

import java.util.stream.Stream
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.cpi.datamodel.repository.CpiMetadataRepository
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.reconciliation.VersionedRecord
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class CpiReconciler(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    dbConnectionManager: DbConnectionManager,
    private val reconcilerFactory: ReconcilerFactory,
    private val reconcilerReader: ReconcilerReader<CpiIdentifier, CpiMetadata>,
    private val reconcilerWriter: ReconcilerWriter<CpiIdentifier, CpiMetadata>,
    private val cpiMetadataRepository: CpiMetadataRepository
) : ReconcilerWrapper {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val dependencies = setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>()
        )
    }

    private var dbReconciler: DbReconcilerReader<CpiIdentifier, CpiMetadata>? = null
    private var reconciler: Reconciler? = null

    private val reconciliationContextFactory = {
        Stream.of(ClusterReconciliationContext(dbConnectionManager))
    }

    override fun stop() {
        dbReconciler?.stop()
        dbReconciler = null
        reconciler?.stop()
        reconciler = null
    }

    override fun updateInterval(intervalMillis: Long) {
        log.debug("Cpi info reconciliation interval set to $intervalMillis ms")

        if (dbReconciler == null) {
            dbReconciler =
                DbReconcilerReader(
                    coordinatorFactory,
                    CpiIdentifier::class.java,
                    CpiMetadata::class.java,
                    dependencies,
                    reconciliationContextFactory,
                    ::getAllCpiInfoDBVersionedRecords
                ).also {
                    it.start()
                }
        }

        if (reconciler == null) {
            reconciler = reconcilerFactory.create(
                dbReader = dbReconciler!!,
                kafkaReader = reconcilerReader,
                writer = reconcilerWriter,
                keyClass = CpiIdentifier::class.java,
                valueClass = CpiMetadata::class.java,
                reconciliationIntervalMs = intervalMillis
            ).also { it.start() }
        } else {
            log.info("Updating Cpi Info ${Reconciler::class.java.name}")
            reconciler!!.updateInterval(intervalMillis)
        }
    }

    internal fun getAllCpiInfoDBVersionedRecords(context: ReconciliationContext): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>> {
        val cpiMetadata =  cpiMetadataRepository.findAll(context.getOrCreateEntityManager())

       return cpiMetadata.map { result ->
            object : VersionedRecord<CpiIdentifier, CpiMetadata> {
                override val version = result.first
                override val isDeleted = result.second
                override val key = result.third.cpiId
                override val value = result.third   // Please bear in mind that this used to be lazy evaluated.
                                                    // So this might have a performance impact
            }
        }
    }
}
