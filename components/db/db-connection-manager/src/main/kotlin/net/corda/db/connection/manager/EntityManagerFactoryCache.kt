package net.corda.db.connection.manager

import net.corda.orm.JpaEntitiesSet
import java.util.UUID
import javax.persistence.EntityManagerFactory

interface EntityManagerFactoryCache {
    /**
     * Get the [EntityManagerFactory] related to the cluster DB.
     */
    val clusterDbEntityManagerFactory: EntityManagerFactory

    /**
     * Get the [EntityManagerFactory] related to the given [db] from cache or create one if necessary.
     *
     * @param db
     * @return
     */
    fun getOrCreate(db: CordaDb): EntityManagerFactory

    /**
     * Get the [EntityManagerFactory] related to the given [id] and [entitiesSet] from cache or create one if necessary.
     *
     * @param id
     * @param entitiesSet
     * @return
     */
    fun getOrCreate(id: UUID, entitiesSet: JpaEntitiesSet): EntityManagerFactory
}