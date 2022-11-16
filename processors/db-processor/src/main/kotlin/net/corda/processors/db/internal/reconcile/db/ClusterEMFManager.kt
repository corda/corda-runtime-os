package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import javax.persistence.EntityManagerFactory

/**
 * Common class for managing the lifecycle of a single [EntityManagerFactory]
 */
class ClusterEMFManager(
    private val dbConnectionManager: DbConnectionManager
) {
    val emf: EntityManagerFactory
        get() = requireNotNull(_entityManagerFactory) {
            "An attempt was made to try access an entity manager factory for config " +
                    "reconciliation before it was initialized."
        }

    private var _entityManagerFactory: EntityManagerFactory? = null

    fun start() {
        _entityManagerFactory = dbConnectionManager.getClusterEntityManagerFactory()
    }

    fun stop() {
        _entityManagerFactory?.close()
        _entityManagerFactory = null
    }
}