package net.corda.db.connection.manager

import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.orm.JpaEntitiesSet
import javax.persistence.EntityManagerFactory

/**
 * The [DbConnectionManager] is responsible for creating and managing [EntityManagerFactory] objects
 * for databases for which connection details itself are stored in the cluster DB.
 * [EntityManagerFactory] object could be created on demand or returned from a cache.
 *
 * The [DbConnectionManager] needs to be configured with enough data do be able to connect to the cluster DB
 * in order to fetch other DBs' connection details.
 * The [EntityManagerFactory] for the cluster DB itself is also exposed.
 *
 * The [DbConnectionManager] will transition to [LifecycleStatus.UP] once it has checked it has access to the cluster DB.
 *
 * @constructor Create empty Db connection manager
 */
interface DbConnectionManager : Lifecycle {

    /**
     * Get an instance of [EntityManagerFactory] for the named [db] from cache or create one if necessary.
     *
     * @param db Predefined [db] to be used.
     * @return
     */
    fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory

    /**
     * Get an instance of [EntityManagerFactory] for the connection ID. Use cache or create one if necessary.
     *
     * @param name name for the connection to be used.
     * @param privilege required.
     * @param entitiesSet to used with the EntityManager.
     * @return
     */
    fun getOrCreateEntityManagerFactory(name: String, privilege: DbPrivilege, entitiesSet: JpaEntitiesSet): EntityManagerFactory

    /**
     * Persist new DB connection with given [config].
     *
     * Replaces if the connection already exists.
     *
     * @param name name for the connection to be used.
     * @param privilege required.
     * @param config smart config object to be used to create a DataSource using the
     *      [DataSourceFactory.createFromConfig] extension method
     * @param description (optional)
     * @param name of the actor responsible for the insert or update.
     */
    fun putConnection(
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String)

    /**
     * Return the [EntityManagerFactory] object for the cluster DB.
     */
    val clusterDbEntityManagerFactory: EntityManagerFactory

    // lifecycle
    /**
     * Provide bootstrap configuration for the DB Connection Manager.
     *
     * Calling this multiple times will result in an error. An error is also thrown if the [DbConnectionManager]
     * cannot connect to the Cluster DB.
     *
     * @param config The bootstrap configuration to connect to the cluster DB.
     * @throws DBConfigurationException If the bootstrap configuration is provided a second time
     *                                  or if the configuration does not allow access.
     */
    fun bootstrap(config: SmartConfig)
}