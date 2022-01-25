package net.corda.db.connection.manager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.orm.JpaEntitiesSet
import java.util.UUID
import javax.persistence.EntityManagerFactory

// TODO - move to API repo
/**
 * Corda DB Types
 *
 * When UUID is set, it is the PK for the connection details to be fetched from the cluster DB.
 */
enum class CordaDb(val persistenceUnitName: String, val id: UUID? = null) {
    CordaCluster("corda-cluster"),
    RBAC("corda-rbac", UUID.fromString("fd301442-7ac7-11ec-90d6-0242ac120003")),
    Vault("corda-vault"),
    Crypto("corda-crypto"),
}

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
     * Get an instance of [EntityManagerFactory] for the named [DbConnectionID] from cache or create one if necessary.
     *
     * @param db Predefined [db] to be used.
     * @return
     */
    fun getOrCreateEntityManagerFactory(db: CordaDb): EntityManagerFactory

    /**
     * Get an instance of [EntityManagerFactory] for the connection ID. Use cache or create one if necessary.
     *
     * @param connectionID ID for the connection to be used. This corresponds ot the ID in the connections DB.
     * @param entitiesSet to used with the EntityManager.
     * @return
     */
    fun getOrCreateEntityManagerFactory(connectionID: UUID, entitiesSet: JpaEntitiesSet): EntityManagerFactory

    /**
     * Persist new DB connection with given [config].
     *
     * Replaces if the connection already exists.
     *
     * @param connectionID
     * @param config
     */
    fun putConnection(connectionID: UUID, config: SmartConfig)

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
     * @throws ConfigurationException If the bootstrap configuration is provided a second time
     *                                  or if the configuration does not allow access.
     */
    fun bootstrap(config: SmartConfig)
}