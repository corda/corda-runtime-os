package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import javax.persistence.EntityManager

/**
 * Additional context to be included during reconciliation.
 *
 * Context instances must be closed after use to close any created resources.
 */
sealed interface ReconciliationContext : AutoCloseable {
    /**
     * Return existing [EntityManager] or create one if one does not already exist.
     */
    fun getOrCreateEntityManager(): EntityManager

    /**
     * Provides human-readable description of the context
     */
    fun prettyPrint(): String
}

/**
 * Context required for reconciling cluster DBs
 */
class ClusterReconciliationContext(
    private val dbConnectionManager: DbConnectionManager
) : ReconciliationContext {
    private var entityManager: EntityManager? = null

    override fun getOrCreateEntityManager(): EntityManager = entityManager
        ?: dbConnectionManager.getClusterEntityManagerFactory().createEntityManager()
            .also { entityManager = it }

    override fun prettyPrint(): String = "Cluster context"

    override fun close() {
        entityManager?.close()
        entityManager = null
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

    private var entityManager: EntityManager? = null

    override fun getOrCreateEntityManager(): EntityManager = entityManager
        ?: dbConnectionManager
            .getOrCreateEntityManagerFactory(virtualNodeInfo.vaultDmlConnectionId, jpaEntitiesSet, enablePool = false)
            .createEntityManager().also { entityManager = it }

    override fun prettyPrint(): String = "vNode ${virtualNodeInfo.holdingIdentity.shortHash} context"

    override fun close() {
        entityManager?.close()
        entityManager = null
    }
}