package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * Additional context to be included during reconciliation.
 *
 * Context instances must be closed after use to close any created resources.
 */
interface ReconciliationContext : AutoCloseable {
    /**
     * Return existing [EntityManager] or create one if one does not already exist.
     */
    fun getOrCreateEntityManager(): EntityManager
}

/**
 * Context required for reconciling cluster DBs
 */
class ClusterReconciliationContext(
    private val dbConnectionManager: DbConnectionManager
) : ReconciliationContext {
    private var entityManagerFactory: EntityManagerFactory? = null
    private var entityManager: EntityManager? = null

    private fun getOrCreateEntityManagerFactory() = entityManagerFactory
        ?: dbConnectionManager.getClusterEntityManagerFactory()
            .also { entityManagerFactory = it }

    override fun getOrCreateEntityManager(): EntityManager = entityManager
        ?: getOrCreateEntityManagerFactory().createEntityManager()
            .also { entityManager = it }

    override fun close() {
        entityManager?.close()
        entityManager = null
        entityManagerFactory = null
    }
}

/**
 * Context required for reconciling virtual node DBs
 */
class VirtualNodeReconciliationContext(
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesSet: JpaEntitiesSet,
    val virtualNodeInfo: VirtualNodeInfo
) : ReconciliationContext {

    private var entityManagerFactory: EntityManagerFactory? = null
    private var entityManager: EntityManager? = null

    private fun getOrCreateEntityManagerFactory() = entityManagerFactory
        ?: dbConnectionManager.createEntityManagerFactory(virtualNodeInfo.vaultDmlConnectionId, jpaEntitiesSet)
            .also { entityManagerFactory = it }

    override fun getOrCreateEntityManager(): EntityManager = entityManager
        ?: getOrCreateEntityManagerFactory().createEntityManager()
            .also { entityManager = it }

    override fun close() {
        entityManager?.close()
        entityManagerFactory?.close()
        entityManager = null
        entityManagerFactory = null
    }
}