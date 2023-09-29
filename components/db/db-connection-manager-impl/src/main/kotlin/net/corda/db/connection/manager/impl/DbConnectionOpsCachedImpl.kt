package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

/*
 * Implements a simple caching layer by encapsulating an object implementing `DbConnectionOps`
 * (typically an instance of `DbConnectionsOpsImpl`). The reason for this is that connecting to most databases
 * in Corda requires a database lookup in the cluster database to get the connection details for the other database,
 * and that is slow enough to be worth implementing.
 * 
 * So, if you are using this class, you have two levels of cache for database connections:
 * 
 *   1. This cache, which stores `EntityManagerFramework` for specific (name, privilege) combinations
 *   2. The Hikari connection pool cache, which keeps a number of actual network connections available.
 * 
 * Since the EntityManagerFramework objects stored in this cache include database connection details, and
 * potentially those can change, we need a way to clear out EntityManagerFrameworks from this cache when they
 * aren't working since we may have received updated credentials via dynamic configuration, and we don't want
 * to keep retrying with the old connection details.
 * 
 * The `EntityManagerFactory` for the cluster database connection of `DbConnectionManagerImpl` does not go in this
 * cache.
 */

class DbConnectionOpsCachedImpl(
    private val delegate: DbConnectionOps,
    private val entitiesRegistry: JpaEntitiesRegistry
    ): DbConnectionOps by delegate {

    // We should try merging the below two caches into one to, like so, make sure each connection gets one EMF only,
    //  otherwise (i.e. if we get duplicate EMFs for same connection) we end up leaking memory with
    //  duplicate entity proxies loaded in the class loader as identified in CORE-15806.
    private val cache = ConcurrentHashMap<Pair<String,DbPrivilege>, EntityManagerFactory>()

    private val cacheByConnectionId = ConcurrentHashMap<UUID, EntityManagerFactory>()

    private fun removeFromCache(name: String, privilege: DbPrivilege) {
        val entityManagerFactory = cache.remove(Pair(name,privilege))
        entityManagerFactory?.close()
    }

    override fun putConnection(name: String, privilege: DbPrivilege, config: SmartConfig,
                               description: String?, updateActor: String): UUID {
        return delegate.putConnection(name, privilege, config, description, updateActor)
            .apply { removeFromCache(name, privilege) }
    }

    override fun putConnection(entityManager: EntityManager, name: String, privilege: DbPrivilege, config: SmartConfig,
                               description: String?, updateActor: String): UUID {
        return delegate.putConnection(entityManager, name, privilege, config, description, updateActor)
            .apply { removeFromCache(name, privilege) }
    }

    override fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory {
        val entitiesSet =
            entitiesRegistry.get(db.persistenceUnitName) ?:
            throw DBConfigurationException("Entity set for ${db.persistenceUnitName} not found")

        return getOrCreateEntityManagerFactory(
            db.persistenceUnitName,
            privilege,
            entitiesSet)
    }

    override fun getOrCreateEntityManagerFactory(
        name: String,
        privilege: DbPrivilege,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory {
        return cache.computeIfAbsent(Pair(name,privilege)) {
            delegate.getOrCreateEntityManagerFactory(name, privilege, entitiesSet)
        }
    }

    override fun getOrCreateEntityManagerFactory(
        connectionId: UUID,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory {
        return cacheByConnectionId.computeIfAbsent(connectionId) {
            delegate.createEntityManagerFactory(connectionId, entitiesSet)
        }
    }

    override fun getIssuedDataSources(): Collection<DataSource> = delegate.getIssuedDataSources()
}