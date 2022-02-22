package net.corda.db.connection.manager

import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.orm.JpaEntitiesSet
import javax.persistence.EntityManagerFactory

/**
 * Cache of [EntityManagerFactory] objects.
 */
interface EntityManagerFactoryCache {
    /**
     * Get the [EntityManagerFactory] related to the cluster DB.
     */
    val clusterDbEntityManagerFactory: EntityManagerFactory

    /**
     * Get the [EntityManagerFactory] related to the given [db] and [privilege] from cache or create one if necessary.
     *
     * @param db Name of the DB to use.
     * @param privilege [DbPrivilege] required (DML or DDL).
     * @throws [DBConfigurationException] if connection details for the requested DB/Privilege does not exist
     *              or if entities associated to the DB are not defined.
     */
    fun getOrCreate(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory

    /**
     * Get the [EntityManagerFactory] related to the given [name], [privilege] and [entitiesSet] from cache
     * or create one if necessary.
     *
     * @param name Name of the DB to use.
     * @param privilege [DbPrivilege] required (DML or DDL).
     * @param entitiesSet Set of all entities managed by [javax.persistence.EntityManager]s created by the
     *                  [EntityManagerFactory] returned
     * @throws [DBConfigurationException] if connection details for the requested DB/Privilege does not exist.
     */
    fun getOrCreate(name: String, privilege: DbPrivilege, entitiesSet: JpaEntitiesSet): EntityManagerFactory

    /**
     * Delete the [EntityManagerFactory] related to the given [name] and [privilege] from cache.
     *
     * @param name Name of the DB.
     * @param privilege [DbPrivilege] required (DML or DDL).
     */
    fun delete(name: String, privilege: DbPrivilege)
}