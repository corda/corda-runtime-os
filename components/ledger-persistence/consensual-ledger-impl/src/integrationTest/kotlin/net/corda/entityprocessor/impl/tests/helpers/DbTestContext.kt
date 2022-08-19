package net.corda.entityprocessor.impl.tests.helpers

import net.corda.entityprocessor.impl.internal.EntitySandboxServiceImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.virtualnode.VirtualNodeInfo
import java.util.UUID
import javax.persistence.EntityManagerFactory

data class DbTestContext(
    val virtualNodeInfo: VirtualNodeInfo,
    val entitySandboxService: EntitySandboxServiceImpl,
    val sandbox: SandboxGroupContext,
    private val entityManagerFactory: EntityManagerFactory,
    private val dogClass: Class<*>,
    private val catClass: Class<*>,
    val schemaName: String
) {
    fun findDog(dogId: UUID): Any? {
        return find(dogId, dogClass)
    }

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
     * Deletes table content.
     *
     * IMPORTANT:  update this if you add other tables.
     */
    fun deleteFromTables() {
        entityManagerFactory.createEntityManager().transaction {
            it.createQuery("DELETE FROM ${dogClass.simpleName}").executeUpdate()
            it.createQuery("DELETE FROM ${catClass.simpleName}").executeUpdate()
        }
    }
}
