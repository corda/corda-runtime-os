package net.corda.processors.db.internal.reconcile.db

import net.corda.data.config.Configuration
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import org.slf4j.LoggerFactory
import java.util.stream.Stream

class ConfigReconciler(
    coordinatorFactory: LifecycleCoordinatorFactory,
    dbConnectionManager: DbConnectionManager,
    private val reconcilerFactory: ReconcilerFactory,
    private val reconcilerReader: ReconcilerReader<String, Configuration>,
    private val reconcilerWriter: ReconcilerWriter<String, Configuration>
) : ReconcilerWrapper {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val dependencies = setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>()
        )
    }

    private val reconciliationContextFactory = {
        Stream.of(ClusterReconciliationContext(dbConnectionManager))
    }
    private val dbReconciler = DbReconcilerReader(
        coordinatorFactory,
        String::class.java,
        Configuration::class.java,
        dependencies,
        reconciliationContextFactory,
        getAllConfigDBVersionedRecords
    )
    private var reconciler: Reconciler? = null

    override fun close() {
        reconciler?.stop()
        reconciler = null
    }

    override fun updateInterval(intervalMillis: Long) {
        log.debug("Config reconciliation interval set to $intervalMillis ms")
        dbReconciler.start()

        if (reconciler == null) {
            reconciler = reconcilerFactory.create(
                dbReader = dbReconciler,
                kafkaReader = reconcilerReader,
                writer = reconcilerWriter,
                keyClass = String::class.java,
                valueClass = Configuration::class.java,
                reconciliationIntervalMs = intervalMillis
            ).also { it.start() }
        } else {
            log.info("Updating Config ${Reconciler::class.java.name}")
            reconciler!!.updateInterval(intervalMillis)
        }
    }
}
