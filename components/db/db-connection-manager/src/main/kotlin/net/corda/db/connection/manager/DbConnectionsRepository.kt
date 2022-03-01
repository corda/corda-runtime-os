package net.corda.db.connection.manager

import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import java.util.*
import javax.persistence.EntityManager
import javax.sql.DataSource

interface DbConnectionsRepository {
    /**
     * Persist a new or updated DB connection with given [name], [privilege] and [config].
     *
     * @param name
     * @param privilege DML or DDL
     * @param config SmartConfig object to use
     * @param description
     * @param updateActor actor on whose behalf the update is on
     * @return ID of persisted DB connection
     */
    fun put(name: String,
            privilege: DbPrivilege,
            config: SmartConfig,
            description: String?,
            updateActor: String): UUID

    /**
    * Persist a new or updated DB connection with given [name], [privilege] and [config].
    *
    * @param entityManager [EntityManager]
    * @param name
    * @param privilege DML or DDL
    * @param config SmartConfig object to use
    * @param description
    * @param updateActor actor on whose behalf the update is on
    * @return ID of persisted DB connection
    */
    @Suppress("LongParameterList")
    fun put(entityManager: EntityManager,
            name: String,
            privilege: DbPrivilege,
            config: SmartConfig,
            description: String?,
            updateActor: String): UUID

    /**
     * Get DB connection for given [name].
     *
     * @param name
     * @param privilege
     * @return The [DataSource] or null if the connection cannot be found.
     * @throws [DBConfigurationException] if the cluster DB cannot be connected to.
     */
    fun get(name: String, privilege: DbPrivilege): DataSource?

    /**
     * Get DB connection for given configuration.
     *
     * @param config DB config
     */
    fun get(config: SmartConfig): DataSource

    /**
     * Get cluster DB [DataSource]
     *
     * @return The cluster DB [DataSource]
     */
    fun getClusterDataSource(): DataSource
}