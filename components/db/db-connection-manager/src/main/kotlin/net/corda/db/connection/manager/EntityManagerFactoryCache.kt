package net.corda.db.connection.manager

import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.orm.JpaEntitiesSet
import javax.persistence.EntityManagerFactory

interface EntityManagerFactoryCache {
    /**
     * Get the [EntityManagerFactory] related to the cluster DB.
     */
    val clusterDbEntityManagerFactory: EntityManagerFactory

    /**
     * Get the [EntityManagerFactory] related to the given [db] from cache or create one if necessary.
     *
     * @param db to get the connection for
     * @param privilege DDL or DML connection
     * @return
     */
    fun getOrCreate(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory

    /**
     * Get the [EntityManagerFactory] related to the given [id] and [entitiesSet] from cache or create one if necessary.
     *
     * @param name of the connection
     * @param privilege required privilege for the connecction
     * @param entitiesSet
     * @return
     */
    fun getOrCreate(name: String, privilege: DbPrivilege, entitiesSet: JpaEntitiesSet): EntityManagerFactory
}