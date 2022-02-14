package net.corda.db.connection.manager

import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import javax.sql.DataSource

interface DbConnectionsRepository {
    /**
     * Initialise the [DbConnectionsRepository] with the given Cluster DB config.
     *
     * This also validates we can connect to the configured cluster DB and retries until it is successful.
     */
    fun initialise(config: SmartConfig)

    /**
     * Persist a new or updated DB connection with given [name], [privilege] and [config].
     *
     * @param name
     * @param privilege DML or DDL
     * @param config SmartConfig object to use
     * @param description
     * @param updateActor actor on whose behalf the update is on
     */
    fun put(name: String,
            privilege: DbPrivilege,
            config: SmartConfig,
            description: String?,
            updateActor: String)

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
     * Get the main cluster DB connection.
     *
     * @throws [DBConfigurationException] if the cluster DB cannot be connected to.
     */
    val clusterDataSource: DataSource
}