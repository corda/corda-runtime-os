package net.corda.db.connection.manager

import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.JpaEntitiesSet
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

/**
 * The [DbConnectionOps] is responsible for creating and managing [EntityManagerFactory] objects
 * for databases for which connection details itself are stored in the cluster DB.
 * Internally, the [EntityManagerFactory] objects could be cached, so the [getOrCreateEntityManagerFactory] method
 * could create the [EntityManagerFactory] on demand or return one from a cache.
 *
 * The [DbConnectionOps] needs to be configured to be able to connect to the cluster DB
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
 */
interface DbConnectionOps {

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
    fun putConnection(name: String,
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
    fun putConnection(entityManager: EntityManager,
                      name: String,
                      privilege: DbPrivilege,
                      config: SmartConfig,
                      description: String?,
                      updateActor: String): UUID

    /**
     * Get cluster DB [DataSource]
     *
     * @return cluster DB [DataSource]
     */
    fun getClusterDataSource(): DataSource

    /**
     * Create a datasource from a connectionId. Can be used to interaction with virtual node DBs.
     *
     * @param connectionId
     * @return [CloseableDataSource] instance
     */
    fun createDatasource(connectionId: UUID): DataSource

    /**
     * Get DB connection for given [name].
     *
     * @param name
     * @param privilege
     * @return The [DataSource] or null if the connection cannot be found.
     * @throws [DBConfigurationException] if the cluster DB cannot be connected to.
     */
    fun getDataSource(name: String, privilege: DbPrivilege): DataSource?

    /**
     * Get DB connection for given configuration.
     *
     * @param config DB config
     */
    fun getDataSource(config: SmartConfig): CloseableDataSource

    /**
     * Get cluster DB [EntityManagerFactory]
     *
     * @return cluster DB [EntityManagerFactory]
     */
    fun getClusterEntityManagerFactory(): EntityManagerFactory

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
    @Suppress("LongParameterList")
    fun getOrCreateEntityManagerFactory(name: String, privilege: DbPrivilege, entitiesSet: JpaEntitiesSet):
            EntityManagerFactory

    /**
     * Create ean [EntityManagerFactory] for a given connection ID.
     *
     * A new EMF should be create and impelemenations of this class should not cache it.
     *
     * @param connectionId
     * @param entitiesSet Set of all entities managed by [javax.persistence.EntityManager]s created by the
     *                  [EntityManagerFactory] returned
     * @return
     */
    fun createEntityManagerFactory(connectionId: UUID, entitiesSet: JpaEntitiesSet): EntityManagerFactory
}