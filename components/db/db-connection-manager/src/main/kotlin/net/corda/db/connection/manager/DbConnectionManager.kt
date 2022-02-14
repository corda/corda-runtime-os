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
 * Internally, the [EntityManagerFactory] objects could be cached, so the [getOrCreateEntityManagerFactory] method
 * could create the [EntityManagerFactory] on demand or return one from a cache.
 *
 * The [DbConnectionManager] needs to be configured to be able to connect to the cluster DB
 * in order to fetch other DBs' connection details.
 * The [EntityManagerFactory] for the cluster DB itself is also cached and exposed.
 *
 * The [getOrCreateEntityManagerFactory] method can be invoked with a [CordaDb] and [DbPrivilege] parameter to
 * support cases for "well known" DBs (e.g. RBAC). For DBs that are dynamically added during the lifecycle of the
 * Corda cluster, e.g. VNode Vault or VNode Crypto DBs, the name, [DbPrivilege] and [JpaEntitiesSet] must be provided,
 * where name is a unique name identifying the DB (e.g. vnode_123_vault) and [JpaEntitiesSet] contains all the JPA
 * entities managed for this DB. The [JpaEntitiesSet] could be a "fixed" set (e.g. vnode crypto DB), or a dynamic set
 * depending on the CorDapps related to the VNode.
 *
 * The [DbConnectionManager] will transition to [LifecycleStatus.UP] once it has checked it has access to the cluster DB.
 *
 * @constructor Create empty Db connection manager
 */
interface DbConnectionManager : Lifecycle {

    /**
     * Get an instance of [EntityManagerFactory] for the named [db] from cache or create one if necessary.
     *
     * @param db Name of the DB to use.
     * @param privilege [DbPrivilege] required (DML or DDL).
     * @return [EntityManagerFactory] from cache, or created on demand.
     * @throws [DBConfigurationException] if connection details for the requested DB/Privilege does not exist
     *              or if entities associated to the DB are not defined.
     */
    fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory

    /**
     * Get an instance of [EntityManagerFactory] for the connection ID. Use cache or create one if necessary.
     *
     * @param name name for the connection to be used.
     * @param privilege [DbPrivilege] required (DML or DDL).
     * @param entitiesSet Set of all entities managed by [javax.persistence.EntityManager]s created by the
     *                  [EntityManagerFactory] returned
     * @return [EntityManagerFactory] from cache, or created on demand.
     * @throws [DBConfigurationException] if connection details for the requested DB/Privilege does not exist.
     */
    fun getOrCreateEntityManagerFactory(name: String, privilege: DbPrivilege, entitiesSet: JpaEntitiesSet):
            EntityManagerFactory

    /**
     * Persist new DB connection with given [config].
     *
     * Replaces if the connection already exists.
     * The [name] and [privilege] pair are unique in the DB Connections Configuration database.
     *
     * @param name Name of the DB to use.
     * @param privilege [DbPrivilege] (DML or DDL).
     * @param config smart config object to be used to create a DataSource using the
     *      [DataSourceFactory.createFromConfig] extension method. This should contain all JDBC connection details
     *      needed to create a [javax.sql.DataSource] from [DataSourceFactory].
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