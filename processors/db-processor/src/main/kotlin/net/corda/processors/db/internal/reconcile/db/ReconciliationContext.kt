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
interface ReconciliationContext : AutoCloseable {
    val entityManager: EntityManager
}

/**
 * Context required for reconciling cluster DBs
 */
class ClusterReconciliationContext(
    dbConnectionManager: DbConnectionManager
) : ReconciliationContext {
    private val entityManagerFactory = dbConnectionManager.getClusterEntityManagerFactory()
    override val entityManager: EntityManager = entityManagerFactory.createEntityManager()

    override fun close() = entityManager.close()
}

/**
 * Context required for reconciling virtual node DBs
 */
class VirtualNodeReconciliationContext(
    dbConnectionManager: DbConnectionManager,
    jpaEntitiesSet: JpaEntitiesSet,
    val virtualNodeInfo: VirtualNodeInfo
) : ReconciliationContext {

    private val entityManagerFactory = dbConnectionManager.createEntityManagerFactory(
        virtualNodeInfo.vaultDmlConnectionId,
        jpaEntitiesSet
    )
    override val entityManager: EntityManager = entityManagerFactory.createEntityManager()

    override fun close() {
        entityManager.close()
        entityManagerFactory.close()
    }
}