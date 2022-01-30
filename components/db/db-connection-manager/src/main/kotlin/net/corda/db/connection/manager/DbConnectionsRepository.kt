package net.corda.db.connection.manager

import net.corda.libs.configuration.SmartConfig
import java.util.UUID
import javax.sql.DataSource

interface DbConnectionsRepository {
    /**
     * Initialise the [DbConnectionsRepository] with the given Cluster DB config.
     *
     * This also validates we can connect to the configured cluster DB and retries until it is successful.
     */
    fun initialise(config: SmartConfig)

    /**
     * Persist a new or updated DB connection with given [connectionID] and [config].
     *
     * @param connectionID
     * @param config
     */
    fun put(connectionID: UUID, config: SmartConfig)

    /**
     * Get DB connection for given [connectionID].
     *
     * @param connectionID
     */
    fun get(connectionID: UUID): DataSource

    /**
     * Get the main cluster DB connection.
     */
    val clusterDataSource: DataSource
}