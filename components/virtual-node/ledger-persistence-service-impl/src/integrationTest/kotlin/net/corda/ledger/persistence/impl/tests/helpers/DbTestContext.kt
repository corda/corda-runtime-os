package net.corda.ledger.persistence.impl.tests.helpers

import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.persistence.common.EntitySandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.virtualnode.VirtualNodeInfo
import javax.persistence.EntityManagerFactory

data class DbTestContext(
    val virtualNodeInfo: VirtualNodeInfo,
    val entitySandboxService: EntitySandboxService,
    val sandbox: SandboxGroupContext,
    private val entityManagerFactory: EntityManagerFactory,
    val schemaName: String
) {
    fun find(id: Any, clazz: Class<*>): Any? {
        entityManagerFactory.createEntityManager().use {
            return it.find(clazz, id)
        }
    }

    /** Persists DIRECTLY to the database */
    fun persist(any: Any) {
        entityManagerFactory.createEntityManager().transaction {
            it.persist(any)
        }
    }

    /**
     * Deletes table content (manually).
     */
    fun deleteFromTables() {
        entityManagerFactory.createEntityManager().transaction {
            // it.createQuery("DELETE FROM ${dogClass.simpleName}").executeUpdate()
        }
    }
}
